package com.autotestfix.backend.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestExecutionControllerTest {

    private static TestExecutionController controllerReturning(MavenTestRunner.RunOutcome outcome) {
        MavenTestRunner fake = new MavenTestRunner(5) {
            @Override
            public RunOutcome run(Path repoDir) {
                return outcome;
            }
        };
        return new TestExecutionController(new TestExecutionService(fake));
    }

    @Test
    void invalidPathReturns400() {
        TestExecutionController controller = controllerReturning(null);

        ResponseEntity<TestExecutionResult> response =
                controller.runTests(new TestExecutionController.RunTestsRequest(""));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ERROR", response.getBody().status());
    }

    @Test
    void validRepositoryReturns200(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        TestExecutionController controller = controllerReturning(new MavenTestRunner.RunOutcome(
                List.of("mvn", "test"), 0, false, "BUILD SUCCESS", false));

        ResponseEntity<TestExecutionResult> response = controller.runTests(
                new TestExecutionController.RunTestsRequest(tempDir.toString()));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SUCCESS", response.getBody().status());
    }
}