package io.cortavyn.graph;

import java.util.Map;
import java.util.Objects;

/** Immutable partial state emitted by a graph node. */
public record StateUpdate(Map<String, Object> values) implements NodeResult {
    public StateUpdate { values = Map.copyOf(Objects.requireNonNull(values, "values must not be null")); }
    public static StateUpdate empty() { return new StateUpdate(Map.of()); }
    @Override public StateUpdate update() { return this; }
}
