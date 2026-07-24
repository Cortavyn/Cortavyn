package io.cortavyn.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class GraphDefinitionTest {
    @Test
    void exposesNodesInDefinitionOrder() {
        assertEquals("plan", new GraphDefinition(List.of(new GraphNode("plan"))).nodes().getFirst().id());
    }
}
