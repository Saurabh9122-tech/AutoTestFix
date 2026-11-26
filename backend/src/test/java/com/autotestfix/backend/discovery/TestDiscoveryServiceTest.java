package com.autotestfix.backend.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestDiscoveryServiceTest {

    @TempDir
    Path temporaryDirectory;

    private TestDiscoveryService createService() {

        return new TestDiscoveryService(
                new TestFileScanner()
        );
    }

    @Test
    void shouldDiscoverJUnit5Tests() throws IOException {

        Files.createFile(
                temporaryDirectory.resolve("pom.xml")
        );

        Path testDirectory = Files.createDirectories(
                temporaryDirectory.resolve(
                        "src/test/java/example"
                )
        );

        Files.writeString(
                testDirectory.resolve("UserServiceTest.java"),
                """
                import org.junit.jupiter.api.Test;

                class UserServiceTest {
                    @Test
                    void shouldWork() {
                    }
                }
                """
        );

        TestDiscoveryResult result =
                createService().discoverTests(
                        temporaryDirectory.toString()
                );

        assertEquals("Maven", result.buildTool());
        assertEquals("JUnit 5", result.testingFramework());
        assertEquals(1, result.totalTestFiles());
        assertEquals("SUCCESS", result.status());
    }

    @Test
    void shouldReturnNoTestsWhenNoneExist() throws IOException {

        Files.createFile(
                temporaryDirectory.resolve("pom.xml")
        );

        TestDiscoveryResult result =
                createService().discoverTests(
                        temporaryDirectory.toString()
                );

        assertEquals(0, result.totalTestFiles());
        assertEquals("None", result.testingFramework());
        assertEquals("NO_TESTS_FOUND", result.status());
    }

    @Test
    void shouldRejectInvalidRepository() {

        assertThrows(
                IllegalArgumentException.class,
                () -> createService().discoverTests(
                        temporaryDirectory.resolve("missing").toString()
                )
        );
    }

    @Test
    void shouldRejectEmptyPath() {

        assertThrows(
                IllegalArgumentException.class,
                () -> createService().discoverTests("")
        );
    }
}