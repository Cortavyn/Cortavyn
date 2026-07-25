package io.cortavyn.deep;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Reference store; production applications should bridge this SPI to graph checkpoints or a database. */
public final class InMemoryDeepRunStore implements DeepRunStore {
    private final ConcurrentHashMap<String, DeepPendingRun> values = new ConcurrentHashMap<>();
    @Override public java.util.concurrent.CompletionStage<Void> save(DeepPendingRun run) { values.put(run.threadId(), run); return CompletableFuture.completedFuture(null); }
    @Override public java.util.concurrent.CompletionStage<Optional<DeepPendingRun>> get(String threadId) { return CompletableFuture.completedFuture(Optional.ofNullable(values.get(threadId))); }
    @Override public java.util.concurrent.CompletionStage<Void> delete(String threadId) { values.remove(threadId); return CompletableFuture.completedFuture(null); }
}
