package com.autotestfix.backend.analysis;

import com.autotestfix.backend.execution.MavenTestRunner;
import com.autotestfix.backend.execution.TestExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestFailureAnalysisServiceTest {

    private static final String FAILING_OUTPUT = """
            [ERROR] demo.CalculatorTest.addsTwoNumbers -- Time elapsed: 0.02 s <<< FAILURE!
            org.opentest4j.AssertionFailedError: expected: <6> but was: <5>
            \tat demo.CalculatorTest.addsTwoNumbers(CalculatorTest.java:14)

            [ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0
            [INFO] BUILD FAILURE
            """;

    private static TestFailureAnalysisService serviceReturning(MavenTestRunner.RunOutcome outcome) {
        MavenTestRunner fake = new MavenTestRunner(5) {
            @Override
            public RunOutcome run(Path repoDir) {
                return outcome;
            }
        };
        return new TestFailureAnalysisService(
                new TestExecutionService(fake), new SurefireOutputParser());
    }

    private static void makeMavenProject(Path dir) throws IOException {
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
    }

    @Test
    void failingRunIsAnalyzed(@TempDir Path tempDir) throws IOException {
        makeMavenProject(tempDir);
        var service = serviceReturning(new MavenTestRunner.RunOutcome(
                List.of("mvn", "test"), 1, false, FAILING_OUTPUT, false));

        var report = service.analyzeRepository(tempDir.toString());

        assertEquals("FAILED", report.executionStatus());
        assertEquals("TEST_FAILURES", report.analysis().category());
        assertEquals(1, report.analysis().failedTests().size());
        assertEquals(1, report.analysis().passed());
    }

    @Test
    void timeoutOverridesCategory(@TempDir Path tempDir) throws IOException {
        makeMavenProject(tempDir);
        var service = serviceReturning(new MavenTestRunner.RunOutcome(
                List.of("mvn", "test"), -1, true, "partial", false));

        var report = service.analyzeRepository(tempDir.toString());

        assertEquals("TIMEOUT", report.analysis().category());
    }

    @Test
    void invalidPathIsRejected() {
        var service = serviceReturning(null);
        assertThrows(IllegalArgumentException.class, () -> service.analyzeRepository(""));
    }
}