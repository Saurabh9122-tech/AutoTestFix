package com.autotestfix.backend.localization;

import java.util.List;

/** Full response of the fault localization API. method is always HEURISTIC. */
public record FaultLocalizationResult(
        String repositoryPath,
        String analysisCategory,
        String method,
        String note,
        List<TestLocalization> localizations) {
}