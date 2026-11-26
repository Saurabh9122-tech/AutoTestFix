package com.autotestfix.backend.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestExecutionServiceTest {

    /** A fake runner that returns a prepared outcome instead of starting Maven. */
    private static TestExecutionService serviceReturning(MavenTestRunner.RunOutcome outcome) {
        MavenTestRunner fake = new MavenTestRunner(5) {
            @Override
            public RunOutcome run(Path repoDir) {
                return outcome;
            }
        };
        return new TestExecutionService(fake);
    }

    private static Path mavenProject(Path dir) throws IOException {
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        return dir;
    }

    @Test
    void blankPathIsRejected() {
        TestExecutionService service = serviceReturning(null);
        assertThrows(IllegalArgumentException.class, () -> service.runTests("  "));
    }

    @Test
    void missingFolderIsRejected() {
        TestExecutionService service = serviceReturning(null);
        assertThrows(IllegalArgumentException.class,
                () -> service.runTests("C:\\this\\folder\\does\\not\\exist"));
    }

    @Test
    void folderWithoutPomIsRejected(@TempDir Path tempDir) {
        TestExecutionService service = serviceReturning(null);
        assertThrows(IllegalArgumentException.class, () -> service.runTests(tempDir.toString()));
    }

    @Test
    void exitCodeZeroMeansSuccess(@TempDir Path tempDir) throws IOException {
        mavenProject(tempDir);
        TestExecutionService service = serviceReturning(new MavenTestRunner.RunOutcome(
                List.of("mvn", "test"), 0, false, "BUILD SUCCESS", false));

        TestExecutionResult result = service.runTests(tempDir.toString());

        assertEquals("SUCCESS", result.status());
        assertEquals(0, result.exitCode());
        assertEquals("Maven", result.buildTool());
        assertTrue(result.output().contains("BUILD SUCCESS"));
    }

    @Test
    void nonZeroExitCodeMeansFailed(@TempDir Path tempDir) throws IOException {
        mavenProject(tempDir);
        TestExecutionService service = serviceReturning(new MavenTestRunner.RunOutcome(
                List.of("mvn", "test"), 1, false, "BUILD FAILURE", false));

        TestExecutionResult result = service.runTests(tempDir.toString());

        assertEquals("FAILED", result.status());
        assertEquals(1, result.exitCode());
    }

    @Test
    void timeoutIsReported(@TempDir Path tempDir) throws IOException {
        mavenProject(tempDir);
        TestExecutionService service = serviceReturning(new MavenTestRunner.RunOutcome(
                List.of("mvn", "test"), -1, true, "partial output", false));

        TestExecutionResult result = service.runTests(tempDir.toString());

        assertEquals("TIMEOUT", result.status());
        assertTrue(result.timedOut());
    }
}