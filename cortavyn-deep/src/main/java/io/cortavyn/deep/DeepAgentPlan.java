package io.cortavyn.deep;

import io.cortavyn.graph.CompiledGraph;
import io.cortavyn.graph.GraphState;
import java.util.Objects;

/** A generated plan represented as an executable graph. */
record DeepAgentPlan(CompiledGraph<GraphState> graph) {
    public DeepAgentPlan {
        Objects.requireNonNull(graph, "graph must not be null");
    }
}
