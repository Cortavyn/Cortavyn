package io.cortavyn.deep;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Process-local todo store suitable for tests and ephemeral runs. */
public final class InMemoryDeepTodoStore implements DeepTodoStore {
    private final ConcurrentHashMap<String, List<DeepTodo>> values = new ConcurrentHashMap<>();
    @Override public java.util.concurrent.CompletionStage<Void> replace(String threadId, List<DeepTodo> todos) { values.put(threadId, List.copyOf(todos)); return CompletableFuture.completedFuture(null); }
    @Override public java.util.concurrent.CompletionStage<List<DeepTodo>> read(String threadId) { return CompletableFuture.completedFuture(values.getOrDefault(threadId, List.of())); }
}
