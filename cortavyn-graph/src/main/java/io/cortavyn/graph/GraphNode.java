package io.cortavyn.graph;

import java.util.Objects;

/** A named step in an executable graph. */
public record GraphNode(String id) {
    public GraphNode {
        Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) throw new IllegalArgumentException("id must not be blank");
    }
}
