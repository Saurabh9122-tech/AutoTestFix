package com.autotestfix.backend.localization;

import com.autotestfix.backend.analysis.SurefireOutputParser;
import com.autotestfix.backend.analysis.TestFailureAnalysisService;
import com.autotestfix.backend.execution.MavenTestRunner;
import com.autotestfix.backend.execution.TestExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FaultLocalizationControllerTest {

    private static final String FAILING_OUTPUT = """
            [ERROR] demo.CalculatorTest.addsTwoNumbers -- Time elapsed: 0.02 s <<< FAILURE!
            org.opentest4j.AssertionFailedError: expected: <5> but was: <-1>
            \tat demo.CalculatorTest.addsTwoNumbers(CalculatorTest.java:12)

            [ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0
            [INFO] BUILD FAILURE
            """;

    private static FaultLocalizationController controllerReturning(MavenTestRunner.RunOutcome outcome) {
        MavenTestRunner fake = new MavenTestRunner(5) {
            @Override
            public RunOutcome run(Path repoDir) {
                return outcome;
            }
        };
        TestFailureAnalysisService analysis = new TestFailureAnalysisService(
                new TestExecutionService(fake), new SurefireOutputParser());
        return new FaultLocalizationController(
                new FaultLocalizationService(analysis, new FaultLocalizer()));
    }

    @Test
    void invalidPathReturns400() {
        var controller = controllerReturning(null);

        ResponseEntity<Object> response =
                controller.localize(new FaultLocalizationController.LocalizeRequest(""));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void failingProjectReturnsSuspects(@TempDir Path repo) throws IOException {
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
        Files.createDirectories(repo.resolve("src/main/java/demo"));
        Files.createDirectories(repo.resolve("src/test/java/demo"));
        Files.writeString(repo.resolve("src/main/java/demo/Calculator.java"), """
                package demo;

                public class Calculator {

                    public int add(int a, int b) {
                        return a - b;
                    }
                }
                """);
        Files.writeString(repo.resolve("src/test/java/demo/CalculatorTest.java"), """
                package demo;

                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;

                class CalculatorTest {

                    private final Calculator calculator = new Calculator();

                    @Test
                    void addsTwoNumbers() {
                        assertEquals(5, calculator.add(2, 3));
                    }
                }
                """);

        var controller = controllerReturning(new MavenTestRunner.RunOutcome(
                List.of("mvn", "test"), 1, false, FAILING_OUTPUT, false));

        ResponseEntity<Object> response = controller.localize(
                new FaultLocalizationController.LocalizeRequest(repo.toString()));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var result = assertInstanceOf(FaultLocalizationResult.class, response.getBody());
        assertEquals("TEST_FAILURES", result.analysisCategory());
        assertEquals(1, result.localizations().size());
        assertEquals("add", result.localizations().get(0).suspects().get(0).methodName());
    }
}