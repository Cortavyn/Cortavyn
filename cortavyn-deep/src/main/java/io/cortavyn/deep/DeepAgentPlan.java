package io.cortavyn.deep;

import io.cortavyn.graph.GraphDefinition;
import java.util.Objects;

/** A generated plan represented as an executable graph. */
public record DeepAgentPlan(GraphDefinition graph) {
    public DeepAgentPlan {
        Objects.requireNonNull(graph, "graph must not be null");
    }
}
