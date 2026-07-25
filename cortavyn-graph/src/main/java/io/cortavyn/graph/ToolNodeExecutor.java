package io.cortavyn.graph;

import java.util.concurrent.CompletionStage;

/** Invokes a graph-owned tool against the current graph state. */
@FunctionalInterface
public interface ToolNodeExecutor {
    CompletionStage<StateUpdateCommand> execute(GraphState state);
}
