package io.cortavyn.deep;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Reference task store. */
public final class InMemoryDeepTaskStore implements DeepTaskStore {
    private final ConcurrentHashMap<String, DeepSubagentTask> tasks = new ConcurrentHashMap<>();
    @Override public java.util.concurrent.CompletionStage<Void> save(DeepSubagentTask task) { tasks.put(task.id(), task); return CompletableFuture.completedFuture(null); }
    @Override public java.util.concurrent.CompletionStage<Optional<DeepSubagentTask>> get(String id) { return CompletableFuture.completedFuture(Optional.ofNullable(tasks.get(id))); }
}
