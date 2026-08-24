package io.cortavyn.deep;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Docker-backed isolated runner with disabled network, hard resource limits, and mounted file transfer. */
public final class ContainerSandbox implements Sandbox, SandboxFiles {
    public record Limits(long memoryBytes, double cpus, int pids, long tmpfsBytes) { public Limits { if (memoryBytes <= 0 || cpus <= 0 || pids <= 0 || tmpfsBytes <= 0) throw new IllegalArgumentException("container limits must be positive"); } public static Limits defaults() { return new Limits(512L * 1024 * 1024, 1.0, 128, 64L * 1024 * 1024); } }
    private final String image; private final Path files; private final Limits limits;
    public ContainerSandbox(String image, Path files, Limits limits) { if (image == null || image.isBlank()) throw new IllegalArgumentException("image must not be blank"); this.image = image; this.files = Objects.requireNonNull(files, "files must not be null").toAbsolutePath().normalize(); this.limits = Objects.requireNonNull(limits, "limits must not be null"); }
    @Override public CompletionStage<SandboxResult> execute(List<String> command, Duration timeout) { if (command.isEmpty()) return CompletableFuture.failedStage(new IllegalArgumentException("command must not be empty")); return CompletableFuture.supplyAsync(() -> { try { Files.createDirectories(files); List<String> docker = new ArrayList<>(List.of("docker", "run", "--rm", "--network", "none", "--memory=" + limits.memoryBytes(), "--cpus=" + limits.cpus(), "--pids-limit=" + limits.pids(), "--read-only", "--tmpfs", "/tmp:rw,noexec,nosuid,size=" + limits.tmpfsBytes(), "--volume", files + ":/workspace:rw", "--workdir", "/workspace", image)); docker.addAll(command); Process process = new ProcessBuilder(docker).start(); CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> read(process.getInputStream())); CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream())); if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) { process.destroyForcibly(); throw new IllegalStateException("container command timed out"); } return new SandboxResult(process.exitValue(), stdout.join(), stderr.join()); } catch (IOException | InterruptedException failure) { if (failure instanceof InterruptedException) Thread.currentThread().interrupt(); throw new IllegalStateException("container sandbox execution failed", failure); } }); }
    @Override public CompletionStage<Void> putFile(String path, byte[] content) { Path target = safe(path); return CompletableFuture.runAsync(() -> { try { Files.createDirectories(Objects.requireNonNull(target.getParent())); Files.write(target, content); } catch (IOException failure) { throw new IllegalStateException("could not upload sandbox file", failure); } }); }
    @Override public CompletionStage<byte[]> getFile(String path) { Path target = safe(path); return CompletableFuture.supplyAsync(() -> { try { return Files.readAllBytes(target); } catch (IOException failure) { throw new IllegalStateException("could not download sandbox file", failure); } }); }
    private Path safe(String path) { Path target = files.resolve(path).normalize(); if (!target.startsWith(files)) throw new IllegalArgumentException("sandbox path escapes mounted files"); return target; }
    private static String read(java.io.InputStream stream) { try { return new String(stream.readAllBytes(), StandardCharsets.UTF_8); } catch (IOException failure) { throw new IllegalStateException(failure); } }
}
