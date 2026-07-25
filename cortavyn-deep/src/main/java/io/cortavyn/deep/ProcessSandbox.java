package io.cortavyn.deep;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Opt-in local process runner rooted at a caller-supplied working directory. Not suitable for untrusted commands. */
public final class ProcessSandbox implements Sandbox {
    private final Path workingDirectory;
    public ProcessSandbox(Path workingDirectory) { this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory must not be null").toAbsolutePath().normalize(); }
    @Override public java.util.concurrent.CompletionStage<SandboxResult> execute(List<String> command, Duration timeout) {
        if (command.isEmpty()) return CompletableFuture.failedStage(new IllegalArgumentException("command must not be empty"));
        return CompletableFuture.supplyAsync(() -> { try {
            Process process = new ProcessBuilder(command).directory(workingDirectory.toFile()).start();
            // Drain both pipes concurrently. Waiting first can deadlock when a child fills one
            // operating-system pipe before it exits.
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) { process.destroyForcibly(); throw new IllegalStateException("sandbox command timed out"); }
            return new SandboxResult(process.exitValue(), stdout.join(), stderr.join());
        } catch (IOException | InterruptedException exception) { if (exception instanceof InterruptedException) Thread.currentThread().interrupt(); throw new IllegalStateException("sandbox execution failed", exception); } });
    }
    private static String read(java.io.InputStream stream) { try { return new String(stream.readAllBytes(), StandardCharsets.UTF_8); } catch (IOException failure) { throw new IllegalStateException("could not read sandbox output", failure); } }
}
