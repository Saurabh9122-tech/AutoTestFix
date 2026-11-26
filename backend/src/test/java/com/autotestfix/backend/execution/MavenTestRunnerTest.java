package com.autotestfix.backend.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenTestRunnerTest {

    private final MavenTestRunner runner = new MavenTestRunner(30);

    @Test
    void commandIsFixedAndRunsTestGoal(@TempDir Path tempDir) {
        List<String> command = runner.buildCommand(tempDir);

        assertTrue(command.contains("test"));
        assertTrue(command.contains("-B"));
        assertEquals("test", command.get(command.size() - 1));
    }

    @Test
    void projectWrapperIsPreferredWhenPresent(@TempDir Path tempDir) throws IOException {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Files.writeString(tempDir.resolve(windows ? "mvnw.cmd" : "mvnw"), "");

        List<String> command = runner.buildCommand(tempDir);

        assertTrue(command.contains(windows ? "mvnw.cmd" : "mvnw"));
    }
}