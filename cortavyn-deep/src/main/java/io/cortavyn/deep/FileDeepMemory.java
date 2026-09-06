package io.cortavyn.deep;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Durable, namespace-isolated file memory for local agent deployments. */
public final class FileDeepMemory implements DeepMemory {
    private final Path root;
    public FileDeepMemory(Path root) { this.root = Objects.requireNonNull(root, "root must not be null").toAbsolutePath().normalize(); }
    @Override public CompletionStage<String> load(String namespace) { return supply(() -> { Path file = file(namespace); return Files.exists(file) ? Files.readString(file) : ""; }); }
    @Override public CompletionStage<Void> save(String namespace, String content) { return run(() -> { Path file = file(namespace); Files.createDirectories(root); Path temporary = Files.createTempFile(root, ".memory-", ".tmp"); Files.writeString(temporary, Objects.requireNonNull(content, "content must not be null")); Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }); }
    private Path file(String namespace) { if (namespace == null || !namespace.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("memory namespace must use only letters, numbers, '.', '_' or '-'"); return root.resolve(namespace + ".md"); }
    private static <T> CompletionStage<T> supply(IoSupplier<T> supplier) { try { return CompletableFuture.completedFuture(supplier.get()); } catch (IOException failure) { return CompletableFuture.failedStage(failure); } }
    private static CompletionStage<Void> run(IoRunnable action) { try { action.run(); return CompletableFuture.completedFuture(null); } catch (IOException failure) { return CompletableFuture.failedStage(failure); } }
    @FunctionalInterface private interface IoSupplier<T> { T get() throws IOException; }
    @FunctionalInterface private interface IoRunnable { void run() throws IOException; }
}
