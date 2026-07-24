package io.cortavyn.graph;

import java.util.List;
import java.util.Objects;

/** Immutable graph topology; execution semantics are supplied by a GraphExecutor. */
public record GraphDefinition(List<GraphNode> nodes) {
    public GraphDefinition {
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes must not be null"));
    }
}
