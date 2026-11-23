package com.autotestfix.backend.repository;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class RepositoryService {

    public RepositoryAnalysis analyzeRepository(String repositoryPath) {

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

        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IllegalArgumentException(
                    "Invalid repository directory."
            );
        }

        List<String> buildFiles = new ArrayList<>();
        List<String> sourceDirectories = new ArrayList<>();
        List<String> testDirectories = new ArrayList<>();

        String projectType = "Unknown";
        String buildTool = "Unknown";

        // Detect Java and Maven projects
        if (Files.exists(root.resolve("pom.xml"))) {

            projectType = "Java";
            buildTool = "Maven";

            buildFiles.add("pom.xml");
        }

        // Detect Gradle projects
        else if (Files.exists(root.resolve("build.gradle"))
                || Files.exists(root.resolve("build.gradle.kts"))) {

            projectType = "Java";
            buildTool = "Gradle";

            if (Files.exists(root.resolve("build.gradle"))) {
                buildFiles.add("build.gradle");
            }

            if (Files.exists(root.resolve("build.gradle.kts"))) {
                buildFiles.add("build.gradle.kts");
            }
        }

        // Detect JavaScript and TypeScript projects
        else if (Files.exists(root.resolve("package.json"))) {

            projectType = "JavaScript/TypeScript";
            buildTool = "npm";

            buildFiles.add("package.json");
        }

        // Detect Python projects
        else if (Files.exists(root.resolve("requirements.txt"))
                || Files.exists(root.resolve("pyproject.toml"))) {

            projectType = "Python";
            buildTool = "pip/pyproject";

            if (Files.exists(root.resolve("requirements.txt"))) {
                buildFiles.add("requirements.txt");
            }

            if (Files.exists(root.resolve("pyproject.toml"))) {
                buildFiles.add("pyproject.toml");
            }
        }

        // Detect source directories
        detectDirectory(root, "src/main", sourceDirectories);
        detectDirectory(root, "src/test", testDirectories);

        if (!sourceDirectories.contains("src/main")) {
            detectDirectory(root, "src", sourceDirectories);
        }

        // Detect test directories
        detectDirectory(root, "tests", testDirectories);
        detectDirectory(root, "test", testDirectories);

        return new RepositoryAnalysis(
                root.toString(),
                projectType,
                buildTool,
                buildFiles,
                sourceDirectories,
                testDirectories
        );
    }

    private void detectDirectory(
            Path root,
            String directory,
            List<String> results
    ) {

        if (Files.isDirectory(root.resolve(directory))
                && !results.contains(directory)) {

            results.add(directory);
        }
    }
}