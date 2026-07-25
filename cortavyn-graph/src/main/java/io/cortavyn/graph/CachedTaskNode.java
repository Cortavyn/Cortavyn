package io.cortavyn.graph;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Graph-node adapter for a cacheable task. Cache key construction remains application-owned. */
public final class CachedTaskNode<S, I, O> implements GraphNode<S> {
    private final GraphTask<I, O> task;
    private final Function<S, I> input;
    private final Function<I, String> cacheKey;
    private final BiFunction<S, O, StateUpdate> output;
    private final TaskCache cache;
    public CachedTaskNode(GraphTask<I, O> task, Function<S, I> input, Function<I, String> cacheKey,
                          BiFunction<S, O, StateUpdate> output, TaskCache cache) {
        this.task = Objects.requireNonNull(task, "task must not be null"); this.input = Objects.requireNonNull(input, "input must not be null");
        this.cacheKey = Objects.requireNonNull(cacheKey, "cacheKey must not be null"); this.output = Objects.requireNonNull(output, "output must not be null"); this.cache = Objects.requireNonNull(cache, "cache must not be null");
    }
    @Override public CompletionStage<StateUpdate> execute(S state, NodeRuntime runtime) {
        I arguments = input.apply(state); String key = cacheKey.apply(arguments); Object existing = cache.get(key).orElse(null);
        // Cache events remain observable through the same graph stream as node lifecycle events.
        if (existing != null) { runtime.emit(GraphEventType.CACHE_HIT, Map.of("key", key)); return CompletableFuture.completedFuture(output.apply(state, cast(existing))); }
        runtime.emit(GraphEventType.CACHE_MISS, Map.of("key", key));
        return task.execute(arguments).thenApply(value -> { cache.put(key, value); return output.apply(state, value); });
    }
    @SuppressWarnings("unchecked") private O cast(Object value) { return (O) value; }
}
