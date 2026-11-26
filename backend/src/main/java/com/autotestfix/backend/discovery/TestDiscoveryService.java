package com.autotestfix.backend.discovery;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class TestDiscoveryService {

    private final TestFileScanner testFileScanner;

    public TestDiscoveryService(TestFileScanner testFileScanner) {
        this.testFileScanner = testFileScanner;
    }

    public TestDiscoveryResult discoverTests(String repositoryPath) {

        if (repositoryPath == null || repositoryPath.isBlank()) {
            throw new IllegalArgumentException(
                    "Repository path is required."
            );
        }

        Path root;

        try {
            root = Path.of(repositoryPath)
                    .toAbsolutePath()
                    .normalize();
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Invalid repository path."
            );
        }

        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException(
                    "Invalid repository directory."
            );
        }

        String buildTool = detectBuildTool(root);

        try {

            List<String> testFiles =
                    testFileScanner.scanTestFiles(root);

            String framework = detectFramework(root, testFiles);

            return new TestDiscoveryResult(
                    root.toString(),
                    buildTool,
                    framework,
                    testFiles.size(),
                    testFiles,
                    testFiles.isEmpty() ? "NO_TESTS_FOUND" : "SUCCESS"
            );

        } catch (IOException exception) {

            throw new IllegalArgumentException(
                    "Unable to scan repository."
            );
        }
    }

    private String detectBuildTool(Path root) {

        if (Files.exists(root.resolve("pom.xml"))) {
            return "Maven";
        }

        if (Files.exists(root.resolve("build.gradle"))
                || Files.exists(root.resolve("build.gradle.kts"))) {
            return "Gradle";
        }

        return "Unknown";
    }

    private String detectFramework(
            Path root,
            List<String> testFiles
    ) {

        boolean junit5 = false;
        boolean junit4 = false;
        boolean testng = false;

        for (String file : testFiles) {

            Path filePath = root.resolve(file);

            try {

                String content = Files.readString(filePath);

                if (content.contains("org.junit.jupiter")) {
                    junit5 = true;
                }

                if (content.contains("org.junit.Test")
                        || content.contains("org.junit.Before")) {
                    junit4 = true;
                }

                if (content.contains("org.testng")) {
                    testng = true;
                }

            } catch (IOException ignored) {
                // Skip files that cannot be read.
            }
        }

        if (junit5) return "JUnit 5";
        if (junit4) return "JUnit 4";
        if (testng) return "TestNG";

        return testFiles.isEmpty() ? "None" : "Unknown";
    }
}