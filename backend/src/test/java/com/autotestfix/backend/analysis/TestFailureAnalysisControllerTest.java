package com.autotestfix.backend.analysis;

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

class TestFailureAnalysisControllerTest {

    private static TestFailureAnalysisController controllerReturning(MavenTestRunner.RunOutcome outcome) {
        MavenTestRunner fake = new MavenTestRunner(5) {
            @Override
            public RunOutcome run(Path repoDir) {
                return outcome;
            }
        };
        return new TestFailureAnalysisController(new TestFailureAnalysisService(
                new TestExecutionService(fake), new SurefireOutputParser()));
    }

    @Test
    void invalidPathReturns400() {
        var controller = controllerReturning(null);

        ResponseEntity<Object> response =
                controller.analyzeFailures(new TestFailureAnalysisController.AnalyzeRequest(""));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void validRepositoryReturns200WithReport(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        var controller = controllerReturning(new MavenTestRunner.RunOutcome(
                List.of("mvn", "test"), 0, false,
                "[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0\n[INFO] BUILD SUCCESS\n",
                false));

        ResponseEntity<Object> response = controller.analyzeFailures(
                new TestFailureAnalysisController.AnalyzeRequest(tempDir.toString()));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var report = assertInstanceOf(
                TestFailureAnalysisService.AnalysisReport.class, response.getBody());
        assertEquals("ALL_PASSED", report.analysis().category());
        assertEquals(3, report.analysis().testsRun());
    }
}