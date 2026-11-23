package com.autotestfix.backend.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryControllerTest {

    @TempDir
    Path temporaryDirectory;

    private final RepositoryController controller =
            new RepositoryController(new RepositoryService());

    @Test
    void shouldReturnAnalysisForValidRepository()
            throws IOException {

        Files.createFile(
                temporaryDirectory.resolve("pom.xml")
        );

        Map<String, String> request = Map.of(
                "repositoryPath",
                temporaryDirectory.toString()
        );

        ResponseEntity<?> response =
                controller.analyzeRepository(request);

        assertEquals(200, response.getStatusCode().value());

        assertInstanceOf(
                RepositoryAnalysis.class,
                response.getBody()
        );
    }

    @Test
    void shouldRejectMissingRepositoryPath() {

        ResponseEntity<?> response =
                controller.analyzeRepository(Map.of());

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void shouldRejectInvalidRepositoryPath() {

        Map<String, String> request = Map.of(
                "repositoryPath",
                temporaryDirectory.resolve("missing").toString()
        );

        ResponseEntity<?> response =
                controller.analyzeRepository(request);

        assertEquals(400, response.getStatusCode().value());
    }
}