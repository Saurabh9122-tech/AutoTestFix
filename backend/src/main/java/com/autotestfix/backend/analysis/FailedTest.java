package com.autotestfix.backend.analysis;

import java.util.List;

/**
 * Details of one failed test.
 * kind is FAILURE (assertion failed) or ERROR (unexpected exception).
 * projectFrames are the stack frames that are NOT from JUnit, the JDK or other frameworks.
 */
public record FailedTest(
        String className,
        String methodName,
        String kind,
        String exceptionType,
        String exceptionMessage,
        List<StackFrame> projectFrames,
        List<String> stackTrace) {
}