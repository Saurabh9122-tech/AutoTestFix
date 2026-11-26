package com.autotestfix.backend.analysis;

import com.autotestfix.backend.execution.TestExecutionResult;
import com.autotestfix.backend.execution.TestExecutionService;
import org.springframework.stereotype.Service;

@Service
public class TestFailureAnalysisService {

    /** What the API returns: a short execution summary plus the structured analysis. */
    public record AnalysisReport(
            String repositoryPath,
            String executionStatus,
            int exitCode,
            boolean timedOut,
            long durationMillis,
            String message,
            TestAnalysisResult analysis) {
    }

    private final TestExecutionService executionService;
    private final SurefireOutputParser parser;

    public TestFailureAnalysisService(TestExecutionService executionService,
                                      SurefireOutputParser parser) {
        this.executionService = executionService;
        this.parser = parser;
    }

    /** @throws IllegalArgumentException if the repository path is invalid (HTTP 400) */
    public AnalysisReport analyzeRepository(String repositoryPath) {
        TestExecutionResult execution = executionService.runTests(repositoryPath);
        TestAnalysisResult analysis = parser.parse(execution.output());

        if (execution.timedOut()) {
            analysis = analysis.withCategory("TIMEOUT");
        } else if ("ERROR".equals(execution.status())) {
            analysis = analysis.withCategory("EXECUTION_ERROR");
        }

        return new AnalysisReport(
                execution.repositoryPath(),
                execution.status(),
                execution.exitCode(),
                execution.timedOut(),
                execution.durationMillis(),
                execution.message(),
                analysis);
    }
}