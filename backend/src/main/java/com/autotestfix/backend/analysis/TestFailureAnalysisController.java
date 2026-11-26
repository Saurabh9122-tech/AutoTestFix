package com.autotestfix.backend.analysis;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
public class TestFailureAnalysisController {

    public record AnalyzeRequest(String repositoryPath) {
    }

    private final TestFailureAnalysisService service;

    public TestFailureAnalysisController(TestFailureAnalysisService service) {
        this.service = service;
    }

    @PostMapping("/failures")
    public ResponseEntity<Object> analyzeFailures(@RequestBody AnalyzeRequest request) {
        String path = request == null ? null : request.repositoryPath();
        try {
            TestFailureAnalysisService.AnalysisReport report = service.analyzeRepository(path);
            if ("EXECUTION_ERROR".equals(report.analysis().category())) {
                return ResponseEntity.internalServerError().body(report);
            }
            return ResponseEntity.ok(report);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}