package io.cortavyn.chat;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** A process-local ToolStore useful for examples and tests; production applications should inject durable storage. */
public final class InMemoryToolStore implements ToolStore {
    private final Map<String, Map<String, Object>> namespaces = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Map<String, Object>> read(String namespace) {
        return CompletableFuture.completedFuture(namespaces.getOrDefault(requireNamespace(namespace), Map.of()));
    }

    @Override
    public CompletionStage<Void> write(String namespace, Map<String, Object> values) {
        namespaces.put(requireNamespace(namespace), Map.copyOf(Objects.requireNonNull(values, "values must not be null")));
        return CompletableFuture.completedFuture(null);
    }

    private static String requireNamespace(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("namespace must not be blank");
        return value;
    }
}
