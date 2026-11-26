package com.autotestfix.backend.analysis;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the console output of "mvn test" (Maven Surefire 3.x format)
 * and extracts test counts, failed tests and stack traces.
 */
@Component
public class SurefireOutputParser {

    private static final int MAX_TRACE_LINES = 80;

    /** Removes the leading "[INFO] ", "[ERROR] " or "[WARNING] " from a line. */
    private static final Pattern LEVEL_PREFIX = Pattern.compile("^\\[(?:INFO|ERROR|WARNING)]\\s?");

    /** "Tests run: 2, Failures: 1, Errors: 0, Skipped: 0" (the final summary has no "Time elapsed"). */
    private static final Pattern SUMMARY = Pattern.compile(
            "^Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+),\\s*Skipped:\\s*(\\d+)\\s*$");

    /** "[ERROR] demo.CalculatorTest.addsTwoNumbers -- Time elapsed: 0.02 s <<< FAILURE!" */
    private static final Pattern FAILED_HEADER = Pattern.compile(
            "^(?:\\[ERROR]\\s+)?([\\w.$]+)\\.([^\\s.]+)\\s+(?:--\\s+)?Time elapsed:\\s*[\\d.,]+\\s*s\\s+<<<\\s+(FAILURE|ERROR)!\\s*$");

    /** "at demo.Calculator.add(Calculator.java:5)" */
    private static final Pattern FRAME = Pattern.compile(
            "^\\s*at\\s+(?:[\\w.$]+/)?([\\w.$]+)\\.([\\w$<>]+)\\(([^:)]*)(?::(\\d+))?\\)");

    /** "[ERROR] /path/Calculator.java:[5,16] cannot find symbol" */
    private static final Pattern COMPILE_ERROR = Pattern.compile(
            "^\\[ERROR]\\s+(.+\\.java:\\[\\d+,\\d+].*)$");

    private static final List<String> FRAMEWORK_PREFIXES = List.of(
            "java.", "javax.", "jdk.", "sun.", "com.sun.",
            "org.junit.", "org.opentest4j.", "org.apache.", "org.springframework.");

    public TestAnalysisResult parse(String output) {
        String text = output == null ? "" : output;
        String[] lines = text.split("\\R");

        int run = 0;
        int failures = 0;
        int errors = 0;
        int skipped = 0;
        boolean summaryFound = false;
        List<FailedTest> failedTests = new ArrayList<>();
        List<String> compilationErrors = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            Matcher header = FAILED_HEADER.matcher(line);
            if (header.matches()) {
                List<String> block = new ArrayList<>();
                int j = i + 1;
                while (j < lines.length
                        && block.size() < MAX_TRACE_LINES
                        && isTraceLine(lines[j])) {
                    block.add(lines[j]);
                    j++;
                }
                failedTests.add(buildFailedTest(
                        header.group(1), header.group(2), header.group(3), block));
                i = j - 1;
                continue;
            }

            String stripped = LEVEL_PREFIX.matcher(line).replaceFirst("").trim();
            Matcher summary = SUMMARY.matcher(stripped);
            if (summary.matches()) {
                summaryFound = true;
                run += Integer.parseInt(summary.group(1));
                failures += Integer.parseInt(summary.group(2));
                errors += Integer.parseInt(summary.group(3));
                skipped += Integer.parseInt(summary.group(4));
                continue;
            }

            Matcher compile = COMPILE_ERROR.matcher(line);
            if (compile.matches()) {
                compilationErrors.add(compile.group(1).trim());
            }
        }

        int passed = Math.max(0, run - failures - errors - skipped);
        String category = decideCategory(text, run, failures, errors,
                failedTests, compilationErrors);

        return new TestAnalysisResult(category, summaryFound, run, passed,
                failures, errors, skipped, failedTests, compilationErrors);
    }

    private boolean isTraceLine(String line) {
        return !line.isBlank()
                && !line.startsWith("[INFO]")
                && !line.startsWith("[ERROR]")
                && !line.startsWith("[WARNING]");
    }

    private FailedTest buildFailedTest(String className, String methodName,
                                       String kind, List<String> block) {
        String exceptionType = "Unknown";
        String exceptionMessage = "";

        if (!block.isEmpty()) {
            String first = block.get(0).trim();
            int idx = first.indexOf(": ");
            String candidateType = idx > 0 ? first.substring(0, idx) : first;
            if (candidateType.matches("[\\w.$]+")) {
                exceptionType = candidateType;
                exceptionMessage = idx > 0 ? first.substring(idx + 2) : "";
            } else {
                exceptionMessage = first;
            }
            // Messages can span several lines, up to the first "at ..." line.
            StringBuilder message = new StringBuilder(exceptionMessage);
            for (int k = 1; k < block.size(); k++) {
                String l = block.get(k);
                if (l.trim().startsWith("at ") || l.startsWith("Caused by")) {
                    break;
                }
                message.append('\n').append(l);
            }
            exceptionMessage = message.toString();
        }

        List<StackFrame> projectFrames = new ArrayList<>();
        for (String l : block) {
            Matcher m = FRAME.matcher(l);
            if (m.find()) {
                String frameClass = m.group(1);
                if (!isFrameworkClass(frameClass)) {
                    int lineNumber = m.group(4) == null ? -1 : Integer.parseInt(m.group(4));
                    projectFrames.add(new StackFrame(frameClass, m.group(2), m.group(3), lineNumber));
                }
            }
        }

        return new FailedTest(className, methodName, kind, exceptionType,
                exceptionMessage, projectFrames, new ArrayList<>(block));
    }

    private boolean isFrameworkClass(String className) {
        for (String prefix : FRAMEWORK_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String decideCategory(String text, int run, int failures, int errors,
                                  List<FailedTest> failedTests, List<String> compilationErrors) {
        if (!compilationErrors.isEmpty() || text.contains("COMPILATION ERROR")) {
            return "COMPILATION_ERROR";
        }
        if (failures + errors > 0 || !failedTests.isEmpty()) {
            return "TEST_FAILURES";
        }
        if (text.contains("BUILD SUCCESS")) {
            return run == 0 ? "NO_TESTS" : "ALL_PASSED";
        }
        return "BUILD_ERROR";
    }
}