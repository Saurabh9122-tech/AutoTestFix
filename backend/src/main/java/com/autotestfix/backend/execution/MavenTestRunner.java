package com.autotestfix.backend.execution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs "mvn test" inside a repository folder using ProcessBuilder.
 * The command is fixed. Only the working directory comes from the caller.
 */
@Component
public class MavenTestRunner {

    private static final int MAX_OUTPUT_CHARS = 500_000;

    private final long timeoutSeconds;

    public MavenTestRunner(
            @Value("${autotestfix.execution.timeout-seconds:300}") long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /** Raw result of one Maven run. */
    public record RunOutcome(List<String> command,
                             int exitCode,
                             boolean timedOut,
                             String output,
                             boolean outputTruncated) {
    }

    /** Builds the fixed Maven command. Prefers the project's own wrapper. */
    public List<String> buildCommand(Path repoDir) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows) {
            String tool = Files.exists(repoDir.resolve("mvnw.cmd")) ? "mvnw.cmd" : "mvn.cmd";
            return List.of("cmd.exe", "/c", tool, "-B", "-ntp", "test");
        }
        if (Files.exists(repoDir.resolve("mvnw"))) {
            return List.of("sh", "mvnw", "-B", "-ntp", "test");
        }
        return List.of("mvn", "-B", "-ntp", "test");
    }

    public RunOutcome run(Path repoDir) throws IOException, InterruptedException {
        List<String> command = buildCommand(repoDir);

        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(repoDir.toFile())
                .redirectErrorStream(true); // merge stderr into stdout

        Process process = builder.start();

        StringBuilder out = new StringBuilder();
        AtomicBoolean truncated = new AtomicBoolean(false);

        // Read output in a separate thread so the process never blocks on a full buffer.
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    synchronized (out) {
                        if (out.length() < MAX_OUTPUT_CHARS) {
                            out.append(line).append('\n');
                        } else {
                            truncated.set(true);
                        }
                    }
                }
            } catch (IOException ignored) {
                // stream closed when the process is killed
            }
        }, "maven-output-reader");
        reader.setDaemon(true);
        reader.start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor();
        }
        reader.join(5000);

        String text;
        synchronized (out) {
            text = out.toString();
        }
        int exitCode = finished ? process.exitValue() : -1;
        return new RunOutcome(command, exitCode, !finished, text, truncated.get());
    }
}