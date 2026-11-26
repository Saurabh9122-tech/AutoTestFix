package com.autotestfix.backend.localization;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * An index of all production classes found under src/main/java.
 * Built fresh for each request, so it never holds stale data.
 */
public class SourceIndex {

    /** One parsed top-level class, with its file and original source lines. */
    public record ClassInfo(String simpleName,
                            String qualifiedName,
                            Path file,
                            TypeDeclaration<?> type,
                            List<String> lines) {
    }

    private static final int MAX_FILES = 2000;

    private final Map<String, ClassInfo> byQualifiedName = new HashMap<>();
    private final Map<String, List<ClassInfo>> bySimpleName = new HashMap<>();

    public static SourceIndex build(Path srcRoot, JavaParser parser) {
        SourceIndex index = new SourceIndex();
        if (!Files.isDirectory(srcRoot)) {
            return index;
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(srcRoot)) {
            files = walk.filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .limit(MAX_FILES)
                    .toList();
        } catch (IOException e) {
            return index;
        }
        for (Path file : files) {
            index.addFile(file, parser);
        }
        return index;
    }

    private void addFile(Path file, JavaParser parser) {
        try {
            ParseResult<CompilationUnit> result = parser.parse(file);
            if (result.getResult().isEmpty()) {
                return;
            }
            CompilationUnit cu = result.getResult().get();
            List<String> lines = Files.readAllLines(file);
            String pkg = cu.getPackageDeclaration()
                    .map(p -> p.getNameAsString())
                    .orElse("");

            for (TypeDeclaration<?> type : cu.getTypes()) {
                String simple = type.getNameAsString();
                String qualified = pkg.isEmpty() ? simple : pkg + "." + simple;
                ClassInfo info = new ClassInfo(simple, qualified, file, type, lines);
                byQualifiedName.put(qualified, info);
                bySimpleName.computeIfAbsent(simple, k -> new ArrayList<>()).add(info);
            }
        } catch (IOException | RuntimeException e) {
            // Unreadable or unparsable file: skip it, the rest of the index still works.
        }
    }

    public ClassInfo findByQualifiedName(String name) {
        return byQualifiedName.get(name);
    }

    public ClassInfo findBySimpleName(String name) {
        List<ClassInfo> matches = bySimpleName.get(name);
        return (matches == null || matches.isEmpty()) ? null : matches.get(0);
    }
}