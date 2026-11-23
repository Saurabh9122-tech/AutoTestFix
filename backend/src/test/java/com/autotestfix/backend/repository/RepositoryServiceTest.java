package com.autotestfix.backend.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryServiceTest {

    private final RepositoryService repositoryService =
            new RepositoryService();

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldDetectJavaMavenProject() throws IOException {

        Files.createFile(temporaryDirectory.resolve("pom.xml"));

        Files.createDirectories(
                temporaryDirectory.resolve("src/main/java")
        );

        Files.createDirectories(
                temporaryDirectory.resolve("src/test/java")
        );

        RepositoryAnalysis result =
                repositoryService.analyzeRepository(
                        temporaryDirectory.toString()
                );

        assertEquals("Java", result.projectType());
        assertEquals("Maven", result.buildTool());

        assertTrue(result.buildFiles().contains("pom.xml"));

        assertTrue(
                result.sourceDirectories().contains("src/main")
        );

        assertTrue(
                result.testDirectories().contains("src/test")
        );
    }

    @Test
    void shouldDetectPythonProject() throws IOException {

        Files.createFile(
                temporaryDirectory.resolve("requirements.txt")
        );

        Files.createDirectories(
                temporaryDirectory.resolve("tests")
        );

        RepositoryAnalysis result =
                repositoryService.analyzeRepository(
                        temporaryDirectory.toString()
                );

        assertEquals("Python", result.projectType());
        assertEquals("pip/pyproject", result.buildTool());

        assertTrue(
                result.buildFiles().contains("requirements.txt")
        );

        assertTrue(
                result.testDirectories().contains("tests")
        );
    }

    @Test
    void shouldDetectJavaScriptProject() throws IOException {

        Files.createFile(
                temporaryDirectory.resolve("package.json")
        );

        RepositoryAnalysis result =
                repositoryService.analyzeRepository(
                        temporaryDirectory.toString()
                );

        assertEquals(
                "JavaScript/TypeScript",
                result.projectType()
        );

        assertEquals("npm", result.buildTool());
    }

    @Test
    void shouldRejectInvalidRepositoryPath() {

        assertThrows(
                IllegalArgumentException.class,
                () -> repositoryService.analyzeRepository(
                        temporaryDirectory.resolve("missing").toString()
                )
        );
    }

    @Test
    void shouldRejectEmptyRepositoryPath() {

        assertThrows(
                IllegalArgumentException.class,
                () -> repositoryService.analyzeRepository("")
        );
    }
}