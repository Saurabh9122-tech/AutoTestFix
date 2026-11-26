package com.autotestfix.backend.localization;

import com.autotestfix.backend.analysis.FailedTest;
import com.autotestfix.backend.analysis.StackFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaultLocalizerTest {

    private final FaultLocalizer localizer = new FaultLocalizer();

    private static final String CALCULATOR = """
            package demo;

            public class Calculator {

                public int add(int a, int b) {
                    return a - b;
                }

                public int subtract(int a, int b) {
                    return a - b;
                }
            }
            """;

    private static final String CALCULATOR_TEST = """
            package demo;

            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;

            class CalculatorTest {

                private final Calculator calculator = new Calculator();

                @Test
                void addsTwoNumbers() {
                    assertEquals(5, calculator.add(2, 3));
                }

                @Test
                void subtractsTwoNumbers() {
                    assertEquals(1, calculator.subtract(3, 2));
                }
            }
            """;

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static FailedTest failure(String testClass, String testMethod, StackFrame... frames) {
        return new FailedTest(testClass, testMethod, "FAILURE",
                "org.opentest4j.AssertionFailedError", "expected: <5> but was: <-1>",
                List.of(frames), List.of());
    }

    @Test
    void testBodyPointsToTheCalledMethod(@TempDir Path repo) throws IOException {
        write(repo.resolve("src/main/java/demo/Calculator.java"), CALCULATOR);
        write(repo.resolve("src/test/java/demo/CalculatorTest.java"), CALCULATOR_TEST);

        FailedTest failed = failure("demo.CalculatorTest", "addsTwoNumbers",
                new StackFrame("demo.CalculatorTest", "addsTwoNumbers", "CalculatorTest.java", 12));

        FaultLocalizationResult result = localizer.localize(repo, "TEST_FAILURES", List.of(failed));

        assertEquals("HEURISTIC", result.method());
        TestLocalization loc = result.localizations().get(0);
        assertEquals(12, loc.failingLine());
        assertEquals("src/test/java/demo/CalculatorTest.java", loc.testFile());

        // Only add() is suspected. subtract() is not called by this test.
        assertEquals(1, loc.suspects().size());
        SuspiciousLocation top = loc.suspects().get(0);
        assertEquals("add", top.methodName());
        assertEquals("demo.Calculator", top.className());
        assertEquals("src/main/java/demo/Calculator.java", top.filePath());
        assertEquals(5, top.startLine());
        assertEquals(7, top.endLine());
        assertEquals("MEDIUM", top.confidence());
        assertTrue(top.sourceSnippet().contains("return a - b;"));
    }

    @Test
    void stackTraceFrameInProductionCodeGivesHighConfidence(@TempDir Path repo) throws IOException {
        write(repo.resolve("src/main/java/demo/Calculator.java"), """
                package demo;

                public class Calculator {

                    public int divide(int a, int b) {
                        return a / b;
                    }
                }
                """);
        write(repo.resolve("src/test/java/demo/CalculatorTest.java"), """
                package demo;

                import org.junit.jupiter.api.Test;

                class CalculatorTest {

                    @Test
                    void divideByZero() {
                        new Calculator().divide(1, 0);
                    }
                }
                """);

        FailedTest failed = failure("demo.CalculatorTest", "divideByZero",
                new StackFrame("demo.Calculator", "divide", "Calculator.java", 6),
                new StackFrame("demo.CalculatorTest", "divideByZero", "CalculatorTest.java", 9));

        SuspiciousLocation top = localizer.localize(repo, "TEST_FAILURES", List.of(failed))
                .localizations().get(0).suspects().get(0);

        assertEquals("divide", top.methodName());
        assertEquals("HIGH", top.confidence());
        assertTrue(top.reasons().stream().anyMatch(r -> r.contains("stack trace")));
    }

    @Test
    void unrelatedTestHasNoSuspects(@TempDir Path repo) throws IOException {
        write(repo.resolve("src/main/java/demo/Calculator.java"), CALCULATOR);
        write(repo.resolve("src/test/java/demo/MiscTest.java"), """
                package demo;

                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;

                class MiscTest {

                    @Test
                    void unrelated() {
                        assertEquals(1, 2);
                    }
                }
                """);

        FailedTest failed = failure("demo.MiscTest", "unrelated",
                new StackFrame("demo.MiscTest", "unrelated", "MiscTest.java", 9));

        TestLocalization loc = localizer.localize(repo, "TEST_FAILURES", List.of(failed))
                .localizations().get(0);

        assertTrue(loc.suspects().isEmpty());
    }

    @Test
    void namingConventionIsUsedAsLowConfidenceFallback(@TempDir Path repo) throws IOException {
        write(repo.resolve("src/main/java/demo/Calculator.java"), CALCULATOR);
        write(repo.resolve("src/test/java/demo/CalculatorTest.java"), """
                package demo;

                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;

                class CalculatorTest {

                    @Test
                    void viaHelper() {
                        assertEquals(5, compute());
                    }

                    private int compute() {
                        return 0;
                    }
                }
                """);

        FailedTest failed = failure("demo.CalculatorTest", "viaHelper",
                new StackFrame("demo.CalculatorTest", "viaHelper", "CalculatorTest.java", 9));

        List<SuspiciousLocation> suspects = localizer.localize(repo, "TEST_FAILURES", List.of(failed))
                .localizations().get(0).suspects();

        assertEquals(1, suspects.size());
        assertNull(suspects.get(0).methodName());
        assertEquals("LOW", suspects.get(0).confidence());
        assertEquals("demo.Calculator", suspects.get(0).className());
    }

    @Test
    void repositoryWithoutSourcesDoesNotCrash(@TempDir Path repo) {
        FailedTest failed = failure("demo.CalculatorTest", "addsTwoNumbers",
                new StackFrame("demo.CalculatorTest", "addsTwoNumbers", "CalculatorTest.java", 12));

        FaultLocalizationResult result = localizer.localize(repo, "TEST_FAILURES", List.of(failed));

        assertEquals(1, result.localizations().size());
        assertTrue(result.localizations().get(0).suspects().isEmpty());
        assertNull(result.localizations().get(0).testFile());
    }
}