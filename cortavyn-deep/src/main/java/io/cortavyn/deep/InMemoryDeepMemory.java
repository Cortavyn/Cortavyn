package io.cortavyn.deep;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** Process-local memory implementation for tests and single-process applications. */
public final class InMemoryDeepMemory implements DeepMemory {
    private final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();
    @Override public CompletionStage<String> load(String namespace) { return CompletableFuture.completedFuture(values.getOrDefault(require(namespace), "")); }
    @Override public CompletionStage<Void> save(String namespace, String content) { values.put(require(namespace), content); return CompletableFuture.completedFuture(null); }
    private static String require(String namespace) { if (namespace == null || namespace.isBlank()) throw new IllegalArgumentException("memory namespace must not be blank"); return namespace; }
}
