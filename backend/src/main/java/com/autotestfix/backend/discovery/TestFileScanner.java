package com.autotestfix.backend.discovery;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@Component
public class TestFileScanner {

    public List<String> scanTestFiles(Path root) throws IOException {

        Path testDirectory = root.resolve("src/test");

        if (!Files.isDirectory(testDirectory)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(testDirectory)) {

            return paths
                    .filter(Files::isRegularFile)
                    .filter(this::isTestFile)
                    .map(path -> root.relativize(path)
                            .toString()
                            .replace("\\", "/"))
                    .sorted()
                    .toList();
        }
    }

    private boolean isTestFile(Path path) {

        String fileName = path.getFileName().toString();

        return fileName.endsWith("Test.java")
                || fileName.endsWith("Tests.java")
                || fileName.endsWith("TestCase.java");
    }
}