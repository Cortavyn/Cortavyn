package io.cortavyn.graph;

import java.util.Map;
import java.util.Objects;

/** Immutable application state supplied to a graph node. */
public record GraphState(Map<String, Object> values) {
    public GraphState {
        values = Map.copyOf(Objects.requireNonNull(values, "values must not be null"));
    }

    public static GraphState empty() {
        return new GraphState(Map.of());
    }
}
