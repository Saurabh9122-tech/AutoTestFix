package com.autotestfix.backend.localization;

import com.autotestfix.backend.analysis.TestFailureAnalysisService;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class FaultLocalizationService {

    private final TestFailureAnalysisService analysisService;
    private final FaultLocalizer localizer;

    public FaultLocalizationService(TestFailureAnalysisService analysisService,
                                    FaultLocalizer localizer) {
        this.analysisService = analysisService;
        this.localizer = localizer;
    }

    /**
     * Runs the tests (Modules 1 and 2), then ranks suspicious production code.
     *
     * @throws IllegalArgumentException if the repository path is invalid (HTTP 400)
     */
    public FaultLocalizationResult localize(String repositoryPath) {
        TestFailureAnalysisService.AnalysisReport report =
                analysisService.analyzeRepository(repositoryPath);
        return localizer.localize(
                Path.of(report.repositoryPath()),
                report.analysis().category(),
                report.analysis().failedTests());
    }
}