package io.cortavyn.graph;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A structured event produced during graph execution. */
public record GraphEvent(GraphEventType type, @Nullable String nodeId, Map<String, Object> data) {
    public GraphEvent { Objects.requireNonNull(type, "type must not be null"); data = Map.copyOf(Objects.requireNonNull(data, "data must not be null")); }
}
