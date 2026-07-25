package io.cortavyn.graph;

import java.util.Objects;

/** A named step in an executable graph. Subclasses may add executable behavior. */
public class GraphNode {
    private final String id;

    public GraphNode(String id) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) throw new IllegalArgumentException("id must not be blank");
    }

    public final String id() {
        return id;
    }
}
