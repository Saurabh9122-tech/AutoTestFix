package com.autotestfix.backend.execution;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/execution")
public class TestExecutionController {

    public record RunTestsRequest(String repositoryPath) {
    }

    private final TestExecutionService service;

    public TestExecutionController(TestExecutionService service) {
        this.service = service;
    }

    @PostMapping("/run-tests")
    public ResponseEntity<TestExecutionResult> runTests(@RequestBody RunTestsRequest request) {
        String path = request == null ? null : request.repositoryPath();
        try {
            TestExecutionResult result = service.runTests(path);
            if ("ERROR".equals(result.status())) {
                return ResponseEntity.internalServerError().body(result);
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(TestExecutionResult.error(path, e.getMessage()));
        }
    }
}