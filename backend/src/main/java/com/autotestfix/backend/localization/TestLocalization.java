package com.autotestfix.backend.localization;

import java.util.List;

/** Suspects for one failed test. failingLine is -1 if unknown. testFile is null if not found. */
public record TestLocalization(
        String testClass,
        String testMethod,
        String testFile,
        int failingLine,
        String exceptionType,
        String exceptionMessage,
        List<SuspiciousLocation> suspects) {
}