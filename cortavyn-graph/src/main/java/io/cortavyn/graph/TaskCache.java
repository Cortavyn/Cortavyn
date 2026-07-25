package io.cortavyn.graph;

import java.util.Optional;

/** Optional cache SPI for deterministic graph tasks. */
public interface TaskCache { Optional<Object> get(String key); void put(String key, Object value); void invalidate(String key); }
