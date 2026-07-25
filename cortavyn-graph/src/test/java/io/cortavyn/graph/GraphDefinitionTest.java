package io.cortavyn.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class GraphDefinitionTest {
    @Test
    void exposesNodesInDefinitionOrder() {
        assertEquals("plan", new GraphDefinition(List.of(new GraphNode("plan"))).nodes().getFirst().id());
    }

    @Test
    void toolNodeEmitsAStateUpdateCommand() {
        ToolNode node = new ToolNode("lookup", state -> java.util.concurrent.CompletableFuture.completedFuture(
                new StateUpdateCommand(java.util.Map.of("weather", "sunny"))));

        GraphState updated = node.execute(new GraphState(java.util.Map.of("city", "Berlin")))
                .thenApply(command -> command.applyTo(new GraphState(java.util.Map.of("city", "Berlin"))))
                .toCompletableFuture().join();

        assertEquals(java.util.Map.of("city", "Berlin", "weather", "sunny"), updated.values());
        assertEquals(node, new GraphDefinition(List.of(node)).nodes().getFirst());
    }
}
