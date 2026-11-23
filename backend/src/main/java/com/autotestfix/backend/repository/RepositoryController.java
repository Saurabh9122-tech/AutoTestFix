package com.autotestfix.backend.repository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/repository")
public class RepositoryController {

    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeRepository(
            @RequestBody Map<String, String> request
    ) {

        String path = request.get("repositoryPath");

        if (path == null || path.isBlank()) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error",
                            "Repository path is required."
                    ));
        }

        try {

            RepositoryAnalysis analysis =
                    repositoryService.analyzeRepository(path);

            return ResponseEntity.ok(analysis);

        } catch (IllegalArgumentException exception) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error",
                            exception.getMessage()
                    ));
        }
    }
}