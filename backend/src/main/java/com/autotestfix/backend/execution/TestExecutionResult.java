package com.autotestfix.backend.execution;

/**
 * Structured response for a test execution.
 * status is one of: SUCCESS, FAILED, TIMEOUT, ERROR.
 */
public record TestExecutionResult(
        String repositoryPath,
        String buildTool,
        String command,
        String status,
        int exitCode,
        boolean timedOut,
        long durationMillis,
        boolean outputTruncated,
        String message,
        String output) {

    public static TestExecutionResult error(String repositoryPath, String message) {
        return new TestExecutionResult(repositoryPath, null, null, "ERROR",
                -1, false, 0, false, message, "");
    }
}