package io.cortavyn.graph;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** A graph node that invokes a tool and returns an explicit state-update command. */
public final class ToolNode extends GraphNode {
    private final ToolNodeExecutor executor;

    public ToolNode(String id, ToolNodeExecutor executor) {
        super(id);
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    public CompletionStage<StateUpdateCommand> execute(GraphState state) {
        return executor.execute(Objects.requireNonNull(state, "state must not be null"));
    }
}
