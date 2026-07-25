package io.cortavyn.graph;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A command emitted by a graph node to merge values into the next graph state. */
public record StateUpdateCommand(Map<String, Object> values) {
    public StateUpdateCommand {
        values = Map.copyOf(Objects.requireNonNull(values, "values must not be null"));
    }

    public GraphState applyTo(GraphState state) {
        Objects.requireNonNull(state, "state must not be null");
        Map<String, Object> updated = new LinkedHashMap<>(state.values());
        updated.putAll(values);
        return new GraphState(updated);
    }
}
