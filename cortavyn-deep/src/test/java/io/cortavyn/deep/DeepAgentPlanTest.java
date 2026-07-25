package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cortavyn.graph.GraphState;
import io.cortavyn.graph.StateGraph;
import io.cortavyn.graph.StateSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeepAgentPlanTest {
    @Test
    void wrapsAnExecutableGraph() {
        var graph = new StateGraph<>(StateSchema.builder(GraphState.adapter()).build())
                .addNode("research", (state, runtime) -> java.util.concurrent.CompletableFuture.completedFuture(io.cortavyn.graph.StateUpdate.empty()))
                .addEdge(StateGraph.START, "research").addEdge("research", StateGraph.END).compile();
        var plan = new DeepAgentPlan(graph);
        assertEquals(graph, plan.graph());
    }
}
