package com.autotestfix.backend.localization;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/localization")
public class FaultLocalizationController {

    public record LocalizeRequest(String repositoryPath) {
    }

    private final FaultLocalizationService service;

    public FaultLocalizationController(FaultLocalizationService service) {
        this.service = service;
    }

    @PostMapping("/faults")
    public ResponseEntity<Object> localize(@RequestBody LocalizeRequest request) {
        String path = request == null ? null : request.repositoryPath();
        try {
            return ResponseEntity.ok(service.localize(path));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}