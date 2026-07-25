package io.cortavyn.graph;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe in-memory task cache. */
public final class InMemoryTaskCache implements TaskCache {
    private final ConcurrentHashMap<String, Object> values = new ConcurrentHashMap<>();
    @Override public Optional<Object> get(String key) { return Optional.ofNullable(values.get(key)); }
    @Override public void put(String key, Object value) { values.put(key, value); }
    @Override public void invalidate(String key) { values.remove(key); }
}
