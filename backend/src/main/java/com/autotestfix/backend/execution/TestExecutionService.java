package com.autotestfix.backend.execution;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class TestExecutionService {

    private final MavenTestRunner runner;

    public TestExecutionService(MavenTestRunner runner) {
        this.runner = runner;
    }

    /**
     * Validates the repository and runs its Maven tests.
     *
     * @throws IllegalArgumentException if the input path is invalid (maps to HTTP 400)
     */
    public TestExecutionResult runTests(String repositoryPath) {
        Path repoDir = validate(repositoryPath);

        long start = System.currentTimeMillis();
        try {
            MavenTestRunner.RunOutcome outcome = runner.run(repoDir);
            long duration = System.currentTimeMillis() - start;

            String status;
            String message;
            if (outcome.timedOut()) {
                status = "TIMEOUT";
                message = "Maven did not finish in time and was stopped.";
            } else if (outcome.exitCode() == 0) {
                status = "SUCCESS";
                message = "Build and all tests passed.";
            } else {
                status = "FAILED";
                message = "Maven exited with code " + outcome.exitCode()
                        + ". Check the output for test failures or build errors.";
            }

            return new TestExecutionResult(
                    repoDir.toString(),
                    "Maven",
                    String.join(" ", outcome.command()),
                    status,
                    outcome.exitCode(),
                    outcome.timedOut(),
                    duration,
                    outcome.outputTruncated(),
                    message,
                    outcome.output());

        } catch (IOException e) {
            return TestExecutionResult.error(repoDir.toString(),
                    "Could not start Maven. Is Maven or JAVA_HOME set up? Details: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TestExecutionResult.error(repoDir.toString(), "Test execution was interrupted.");
        }
    }

    private Path validate(String repositoryPath) {
        if (repositoryPath == null || repositoryPath.isBlank()) {
            throw new IllegalArgumentException("repositoryPath must not be empty.");
        }
        Path repoDir;
        try {
            repoDir = Paths.get(repositoryPath.trim()).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("repositoryPath is not a valid path.");
        }
        if (!Files.isDirectory(repoDir)) {
            throw new IllegalArgumentException("Repository folder does not exist: " + repoDir);
        }
        if (!Files.isRegularFile(repoDir.resolve("pom.xml"))) {
            throw new IllegalArgumentException(
                    "No pom.xml found. Only Maven projects are supported for now.");
        }
        return repoDir;
    }
}