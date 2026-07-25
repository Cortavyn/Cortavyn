package io.cortavyn.graph;

import java.util.Map;
import java.util.Objects;

/** A node scheduled for a future superstep with an isolated input update. */
public record CheckpointTask(String nodeId, Map<String, Object> input) {
    public CheckpointTask {
        if (nodeId == null || nodeId.isBlank()) throw new IllegalArgumentException("nodeId must not be blank");
        input = Map.copyOf(Objects.requireNonNull(input, "input must not be null"));
    }
    public static CheckpointTask of(String nodeId) { return new CheckpointTask(nodeId, Map.of()); }
}
