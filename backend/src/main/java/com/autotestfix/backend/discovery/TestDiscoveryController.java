package com.autotestfix.backend.discovery;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/discovery")
public class TestDiscoveryController {

    private final TestDiscoveryService discoveryService;

    public TestDiscoveryController(
            TestDiscoveryService discoveryService
    ) {
        this.discoveryService = discoveryService;
    }

    @PostMapping("/tests")
    public ResponseEntity<?> discoverTests(
            @RequestBody Map<String, String> request
    ) {

        String repositoryPath = request.get("repositoryPath");

        if (repositoryPath == null || repositoryPath.isBlank()) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error",
                            "Repository path is required."
                    ));
        }

        try {

            TestDiscoveryResult result =
                    discoveryService.discoverTests(repositoryPath);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException exception) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error",
                            exception.getMessage()
                    ));
        }
    }
}