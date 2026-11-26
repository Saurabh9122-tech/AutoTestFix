package com.autotestfix.backend.analysis;

/** One line of a stack trace, e.g. at demo.Calculator.add(Calculator.java:5). line is -1 if unknown. */
public record StackFrame(String className, String methodName, String fileName, int line) {
}