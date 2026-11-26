package com.autotestfix.backend.analysis;

import java.util.List;

/** Structured summary of one Maven test run. */
public record TestAnalysisResult(
        String category,
        boolean summaryFound,
        int testsRun,
        int passed,
        int failures,
        int errors,
        int skipped,
        List<FailedTest> failedTests,
        List<String> compilationErrors) {

    public TestAnalysisResult withCategory(String newCategory) {
        return new TestAnalysisResult(newCategory, summaryFound, testsRun, passed,
                failures, errors, skipped, failedTests, compilationErrors);
    }
}