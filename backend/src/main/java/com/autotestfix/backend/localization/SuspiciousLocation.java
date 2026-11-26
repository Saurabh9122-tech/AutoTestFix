package com.autotestfix.backend.localization;

import java.util.List;

/**
 * One suspected place in the production code.
 * methodName is null when only the whole class is suspected.
 * confidence is HIGH, MEDIUM or LOW (a heuristic estimate, not a guarantee).
 */
public record SuspiciousLocation(
        String filePath,
        String className,
        String methodName,
        int startLine,
        int endLine,
        double score,
        String confidence,
        List<String> reasons,
        String sourceSnippet) {
}