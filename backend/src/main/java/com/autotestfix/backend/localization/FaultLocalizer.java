package com.autotestfix.backend.localization;

import com.autotestfix.backend.analysis.FailedTest;
import com.autotestfix.backend.analysis.StackFrame;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.Type;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Heuristic fault localization. It does NOT prove where a bug is.
 * It ranks production methods using stack traces, the failing test's code
 * (parsed with JavaParser) and test/class naming conventions.
 */
@Component
public class FaultLocalizer {

    public static final String METHOD = "HEURISTIC";
    public static final String DISCLAIMER =
            "Heuristic ranking based on stack traces, the failing test's code and naming "
                    + "conventions. These are suspects, not proven fault locations.";

    private static final int MAX_SUSPECTS = 5;
    private static final int MAX_SNIPPET_LINES = 60;

    private final JavaParser parser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));

    /** Evidence collected for one suspected method (or whole class). */
    private static final class Candidate {
        final SourceIndex.ClassInfo cls;
        final CallableDeclaration<?> callable;
        double score;
        final List<String> reasons = new ArrayList<>();

        Candidate(SourceIndex.ClassInfo cls, CallableDeclaration<?> callable) {
            this.cls = cls;
            this.callable = callable;
        }

        void add(double weight, String reason) {
            if (reasons.contains(reason)) {
                return;
            }
            if (reasons.isEmpty()) {
                score = weight;
            } else {
                // More independent evidence raises the score a little.
                score = Math.min(0.99, Math.max(score, weight) + 0.05);
            }
            reasons.add(reason);
        }
    }

    public FaultLocalizationResult localize(Path repoDir, String category, List<FailedTest> failedTests) {
        SourceIndex index = SourceIndex.build(
                repoDir.resolve("src").resolve("main").resolve("java"), parser);

        List<TestLocalization> localizations = new ArrayList<>();
        for (FailedTest failed : failedTests) {
            localizations.add(localizeOne(repoDir, index, failed));
        }

        String note = localizations.isEmpty()
                ? "No failed tests to localize (analysis category: " + category + "). " + DISCLAIMER
                : DISCLAIMER;
        return new FaultLocalizationResult(repoDir.toString(), category, METHOD, note, localizations);
    }

    private TestLocalization localizeOne(Path repoDir, SourceIndex index, FailedTest failed) {
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        String testClassBase = baseName(failed.className());

        // Evidence 1: production frames in the stack trace.
        int failingLine = -1;
        for (StackFrame frame : failed.projectFrames()) {
            String frameBase = baseName(frame.className());
            if (frameBase.equals(testClassBase)) {
                if (failingLine < 0) {
                    failingLine = frame.line();
                }
                continue;
            }
            SourceIndex.ClassInfo cls = index.findByQualifiedName(frameBase);
            if (cls == null) {
                continue;
            }
            CallableDeclaration<?> callable = findCallableAtLine(cls, frame.line());
            addEvidence(candidates, cls, callable, callable != null ? 0.90 : 0.75,
                    "Appears in the stack trace at " + frame.fileName() + ":" + frame.line());
        }

        // Evidence 2: production code called or created by the failing test method.
        Path testFile = repoDir.resolve("src").resolve("test").resolve("java")
                .resolve(testClassBase.replace('.', '/') + ".java");
        String testFileRelative = null;
        if (Files.isRegularFile(testFile)) {
            testFileRelative = relative(repoDir, testFile);
            analyzeTestBody(testFile, failed.methodName(), index, candidates);
        }

        // Evidence 3: naming convention (CalculatorTest -> Calculator).
        String target = conventionTarget(testClassBase);
        SourceIndex.ClassInfo conventionClass = target == null ? null : index.findBySimpleName(target);
        if (conventionClass != null) {
            String reason = "Test class name '" + simpleName(testClassBase)
                    + "' suggests it tests " + conventionClass.simpleName();
            boolean matched = false;
            for (Candidate c : candidates.values()) {
                if (c.cls == conventionClass) {
                    c.add(0.35, reason);
                    matched = true;
                }
            }
            if (!matched) {
                addEvidence(candidates, conventionClass, null, 0.30, reason);
            }
        }

        List<SuspiciousLocation> suspects = candidates.values().stream()
                .sorted(Comparator.comparingDouble((Candidate c) -> c.score).reversed())
                .limit(MAX_SUSPECTS)
                .map(c -> toSuspect(repoDir, c))
                .toList();

        return new TestLocalization(failed.className(), failed.methodName(), testFileRelative,
                failingLine, failed.exceptionType(), failed.exceptionMessage(), suspects);
    }

    private void analyzeTestBody(Path testFile, String methodName, SourceIndex index,
                                 Map<String, Candidate> candidates) {
        CompilationUnit cu;
        try {
            ParseResult<CompilationUnit> result = parser.parse(testFile);
            if (result.getResult().isEmpty()) {
                return;
            }
            cu = result.getResult().get();
        } catch (IOException | RuntimeException e) {
            return;
        }

        // Parameterized tests are reported like "test[1]"; keep only the method name.
        String cleanName = methodName.replaceAll("[\\[(].*$", "");
        Optional<MethodDeclaration> found = cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals(cleanName))
                .findFirst();
        if (found.isEmpty()) {
            return;
        }
        MethodDeclaration testMethod = found.get();

        // Which variable has which type? (fields of the test class, locals, parameters)
        Map<String, String> varTypes = new HashMap<>();
        for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
            for (VariableDeclarator v : field.getVariables()) {
                registerVariable(varTypes, v);
            }
        }
        for (VariableDeclarator v : testMethod.findAll(VariableDeclarator.class)) {
            registerVariable(varTypes, v);
        }
        for (Parameter p : testMethod.getParameters()) {
            varTypes.put(p.getNameAsString(), typeName(p.getType()));
        }

        // Method calls on production classes.
        for (MethodCallExpr call : testMethod.findAll(MethodCallExpr.class)) {
            String typeName = resolveScopeType(call, varTypes);
            if (typeName == null) {
                continue;
            }
            SourceIndex.ClassInfo cls = index.findBySimpleName(typeName);
            if (cls == null) {
                continue;
            }
            String where = call.getBegin().map(p -> " (test line " + p.line + ")").orElse("");
            MethodDeclaration target = findMethodByName(cls, call.getNameAsString(),
                    call.getArguments().size());
            if (target != null) {
                addEvidence(candidates, cls, target, 0.60,
                        "Called by the failing test: " + typeName + "." + call.getNameAsString()
                                + "()" + where);
            } else {
                addEvidence(candidates, cls, null, 0.30,
                        "Failing test calls " + typeName + "." + call.getNameAsString()
                                + "(), but that method was not found in the class" + where);
            }
        }

        // Classes created by the test (only when nothing else points at that class).
        for (ObjectCreationExpr creation : testMethod.findAll(ObjectCreationExpr.class)) {
            SourceIndex.ClassInfo cls = index.findBySimpleName(creation.getType().getNameAsString());
            if (cls != null && !hasCandidateFor(candidates, cls)) {
                addEvidence(candidates, cls, null, 0.25,
                        "Instantiated by the failing test: " + cls.simpleName());
            }
        }
    }

    // ---------- helpers ----------

    private void registerVariable(Map<String, String> map, VariableDeclarator v) {
        Type type = v.getType();
        String name;
        if (type.isVarType()) {
            name = v.getInitializer()
                    .filter(Expression::isObjectCreationExpr)
                    .map(e -> e.asObjectCreationExpr().getType().getNameAsString())
                    .orElse(null);
        } else {
            name = typeName(type);
        }
        if (name != null) {
            map.put(v.getNameAsString(), name);
        }
    }

    private String typeName(Type type) {
        if (type.isClassOrInterfaceType()) {
            return type.asClassOrInterfaceType().getNameAsString();
        }
        return type.asString();
    }

    /** Works out which class a method call is made on, or null if unknown. */
    private String resolveScopeType(MethodCallExpr call, Map<String, String> varTypes) {
        Optional<Expression> scope = call.getScope();
        if (scope.isEmpty()) {
            return null;
        }
        Expression s = scope.get();
        if (s.isNameExpr()) {
            String name = s.asNameExpr().getNameAsString();
            if (varTypes.containsKey(name)) {
                return varTypes.get(name);
            }
            // Static call such as MathUtil.square(3)
            return Character.isUpperCase(name.charAt(0)) ? name : null;
        }
        if (s.isObjectCreationExpr()) {
            return s.asObjectCreationExpr().getType().getNameAsString();
        }
        if (s.isFieldAccessExpr()) {
            FieldAccessExpr access = s.asFieldAccessExpr();
            if (access.getScope().isThisExpr()) {
                return varTypes.get(access.getNameAsString());
            }
        }
        return null;
    }

    private List<CallableDeclaration<?>> allCallables(SourceIndex.ClassInfo cls) {
        List<CallableDeclaration<?>> all = new ArrayList<>();
        all.addAll(cls.type().getMethods());
        all.addAll(cls.type().getConstructors());
        return all;
    }

    private CallableDeclaration<?> findCallableAtLine(SourceIndex.ClassInfo cls, int line) {
        if (line < 0) {
            return null;
        }
        for (CallableDeclaration<?> c : allCallables(cls)) {
            Optional<Range> range = c.getRange();
            if (range.isPresent() && range.get().begin.line <= line && line <= range.get().end.line) {
                return c;
            }
        }
        return null;
    }

    private MethodDeclaration findMethodByName(SourceIndex.ClassInfo cls, String name, int argCount) {
        List<MethodDeclaration> byName = cls.type().getMethodsByName(name);
        if (byName.isEmpty()) {
            return null;
        }
        for (MethodDeclaration m : byName) {
            if (m.getParameters().size() == argCount) {
                return m;
            }
        }
        return byName.get(0);
    }

    private void addEvidence(Map<String, Candidate> candidates, SourceIndex.ClassInfo cls,
                             CallableDeclaration<?> callable, double weight, String reason) {
        String key = cls.qualifiedName() + "#" + keyPart(callable);
        candidates.computeIfAbsent(key, k -> new Candidate(cls, callable)).add(weight, reason);
    }

    private String keyPart(CallableDeclaration<?> callable) {
        if (callable == null) {
            return "*";
        }
        return String.valueOf(callable.getRange().map(r -> r.begin.line).orElse(-1));
    }

    private boolean hasCandidateFor(Map<String, Candidate> candidates, SourceIndex.ClassInfo cls) {
        for (Candidate c : candidates.values()) {
            if (c.cls == cls) {
                return true;
            }
        }
        return false;
    }

    private SuspiciousLocation toSuspect(Path repoDir, Candidate c) {
        Optional<Range> range = c.callable != null ? c.callable.getRange() : c.cls.type().getRange();
        int start = range.map(r -> r.begin.line).orElse(1);
        int end = range.map(r -> r.end.line).orElse(start);

        String methodName = null;
        if (c.callable != null) {
            methodName = c.callable instanceof ConstructorDeclaration
                    ? "<init>" : c.callable.getNameAsString();
        }

        double rounded = Math.round(c.score * 100.0) / 100.0;
        String confidence = rounded >= 0.80 ? "HIGH" : rounded >= 0.55 ? "MEDIUM" : "LOW";

        return new SuspiciousLocation(
                relative(repoDir, c.cls.file()),
                c.cls.qualifiedName(),
                methodName,
                start,
                end,
                rounded,
                confidence,
                List.copyOf(c.reasons),
                snippet(c.cls.lines(), start, end));
    }

    private String snippet(List<String> lines, int start, int end) {
        int from = Math.max(1, start);
        int to = Math.min(lines.size(), end);
        boolean truncated = false;
        if (to - from + 1 > MAX_SNIPPET_LINES) {
            to = from + MAX_SNIPPET_LINES - 1;
            truncated = true;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = from; i <= to; i++) {
            sb.append(lines.get(i - 1)).append('\n');
        }
        if (truncated) {
            sb.append("// ... (truncated)\n");
        }
        return sb.toString();
    }

    private String relative(Path repoDir, Path file) {
        return repoDir.relativize(file).toString().replace('\\', '/');
    }

    /** "demo.CalculatorTest$Nested" -> "demo.CalculatorTest" */
    private String baseName(String className) {
        int dollar = className.indexOf('$');
        return dollar > 0 ? className.substring(0, dollar) : className;
    }

    private String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    /** CalculatorTest, CalculatorTests, CalculatorIT, TestCalculator -> Calculator */
    private String conventionTarget(String testClassBase) {
        String simple = simpleName(testClassBase);
        for (String suffix : List.of("Tests", "Test", "IT")) {
            if (simple.endsWith(suffix) && simple.length() > suffix.length()) {
                return simple.substring(0, simple.length() - suffix.length());
            }
        }
        if (simple.startsWith("Test") && simple.length() > 4) {
            return simple.substring(4);
        }
        return null;
    }
}