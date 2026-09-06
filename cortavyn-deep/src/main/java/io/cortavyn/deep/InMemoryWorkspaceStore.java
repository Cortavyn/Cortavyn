package io.cortavyn.deep;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;

/** Thread-safe reference implementation of {@link WorkspaceStore}. */
public final class InMemoryWorkspaceStore implements WorkspaceStore {
    private final ConcurrentHashMap<String, File> files = new ConcurrentHashMap<>();
    @Override public CompletionStage<Optional<File>> get(String path) { return CompletableFuture.completedFuture(Optional.ofNullable(files.get(path))); }
    @Override public CompletionStage<Void> put(String path, String content) { files.put(path, new File(content, Instant.now())); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<List<Entry>> list() { return CompletableFuture.completedFuture(files.entrySet().stream().map(entry -> new Entry(entry.getKey(), entry.getValue().content().length(), entry.getValue().modifiedAt())).sorted(java.util.Comparator.comparing(Entry::path)).toList()); }
}
