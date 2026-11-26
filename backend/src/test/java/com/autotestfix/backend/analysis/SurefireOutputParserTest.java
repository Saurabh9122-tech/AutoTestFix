package com.autotestfix.backend.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurefireOutputParserTest {

    private final SurefireOutputParser parser = new SurefireOutputParser();

    private static final String PASSING = """
            [INFO] Running demo.CalculatorTest
            [INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.041 s -- in demo.CalculatorTest
            [INFO]
            [INFO] Results:
            [INFO]
            [INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
            [INFO]
            [INFO] BUILD SUCCESS
            """;

    private static final String ASSERTION_FAILURE = """
            [INFO] Running demo.CalculatorTest
            [ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.05 s <<< FAILURE! -- in demo.CalculatorTest
            [ERROR] demo.CalculatorTest.addsTwoNumbers -- Time elapsed: 0.02 s <<< FAILURE!
            org.opentest4j.AssertionFailedError: expected: <6> but was: <5>
            \tat org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:151)
            \tat org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:633)
            \tat demo.CalculatorTest.addsTwoNumbers(CalculatorTest.java:14)
            \tat java.base/java.lang.reflect.Method.invoke(Method.java:580)

            [INFO]
            [INFO] Results:
            [INFO]
            [ERROR] Failures:
            [ERROR]   CalculatorTest.addsTwoNumbers:14 expected: <6> but was: <5>
            [INFO]
            [ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0
            [INFO]
            [INFO] BUILD FAILURE
            """;

    private static final String EXCEPTION_ERROR = """
            [ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0, Time elapsed: 0.01 s <<< FAILURE! -- in demo.FooTest
            [ERROR] demo.FooTest.boom -- Time elapsed: 0.01 s <<< ERROR!
            java.lang.IllegalStateException
            \tat demo.Foo.run(Foo.java:9)
            \tat demo.FooTest.boom(FooTest.java:7)

            [ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
            [INFO] BUILD FAILURE
            """;

    private static final String COMPILE_ERROR = """
            [ERROR] COMPILATION ERROR :
            [INFO] -------------------------------------------------------------
            [ERROR] /C:/proj/src/main/java/demo/Calculator.java:[5,16] cannot find symbol
            [INFO] 1 error
            [INFO] BUILD FAILURE
            """;

    @Test
    void passingRunIsParsed() {
        TestAnalysisResult result = parser.parse(PASSING);

        assertEquals("ALL_PASSED", result.category());
        assertTrue(result.summaryFound());
        assertEquals(2, result.testsRun());
        assertEquals(2, result.passed());
        assertEquals(0, result.failures());
        assertTrue(result.failedTests().isEmpty());
    }

    @Test
    void assertionFailureIsParsedWithDetails() {
        TestAnalysisResult result = parser.parse(ASSERTION_FAILURE);

        assertEquals("TEST_FAILURES", result.category());
        assertEquals(2, result.testsRun());
        assertEquals(1, result.passed());
        assertEquals(1, result.failures());
        assertEquals(1, result.failedTests().size());

        FailedTest failed = result.failedTests().get(0);
        assertEquals("demo.CalculatorTest", failed.className());
        assertEquals("addsTwoNumbers", failed.methodName());
        assertEquals("FAILURE", failed.kind());
        assertEquals("org.opentest4j.AssertionFailedError", failed.exceptionType());
        assertEquals("expected: <6> but was: <5>", failed.exceptionMessage());
        assertFalse(failed.stackTrace().isEmpty());
    }

    @Test
    void frameworkFramesAreFilteredOut() {
        FailedTest failed = parser.parse(ASSERTION_FAILURE).failedTests().get(0);

        assertEquals(1, failed.projectFrames().size());
        StackFrame frame = failed.projectFrames().get(0);
        assertEquals("demo.CalculatorTest", frame.className());
        assertEquals("addsTwoNumbers", frame.methodName());
        assertEquals("CalculatorTest.java", frame.fileName());
        assertEquals(14, frame.line());
    }

    @Test
    void exceptionWithoutMessageIsParsed() {
        TestAnalysisResult result = parser.parse(EXCEPTION_ERROR);

        assertEquals("TEST_FAILURES", result.category());
        assertEquals(1, result.errors());
        FailedTest failed = result.failedTests().get(0);
        assertEquals("ERROR", failed.kind());
        assertEquals("java.lang.IllegalStateException", failed.exceptionType());
        assertEquals("", failed.exceptionMessage());
        assertEquals(2, failed.projectFrames().size());
        assertEquals("demo.Foo", failed.projectFrames().get(0).className());
        assertEquals(9, failed.projectFrames().get(0).line());
    }

    @Test
    void compilationErrorIsDetected() {
        TestAnalysisResult result = parser.parse(COMPILE_ERROR);

        assertEquals("COMPILATION_ERROR", result.category());
        assertEquals(1, result.compilationErrors().size());
        assertTrue(result.compilationErrors().get(0).contains("Calculator.java:[5,16]"));
    }

    @Test
    void emptyOutputDoesNotCrash() {
        TestAnalysisResult result = parser.parse("");

        assertEquals("BUILD_ERROR", result.category());
        assertFalse(result.summaryFound());
        assertEquals(0, result.testsRun());
    }

    @Test
    void nullOutputDoesNotCrash() {
        assertEquals("BUILD_ERROR", parser.parse(null).category());
    }
}