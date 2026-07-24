package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cortavyn.graph.GraphDefinition;
import io.cortavyn.graph.GraphNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeepAgentPlanTest {
    @Test
    void wrapsAnExecutableGraph() {
        var plan = new DeepAgentPlan(new GraphDefinition(List.of(new GraphNode("research"))));
        assertEquals("research", plan.graph().nodes().getFirst().id());
    }
}
