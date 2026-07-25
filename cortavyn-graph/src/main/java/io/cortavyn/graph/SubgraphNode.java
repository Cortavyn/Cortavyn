package io.cortavyn.graph;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

/** Adapts a compiled child graph as a parent-graph node. */
public final class SubgraphNode<S> implements GraphNode<S> {
    private final CompiledGraph<S> graph;
    private final BiFunction<S, S, StateUpdate> output;

    /**
     * @param output maps the child result back to the parent state update; this keeps parent and child schemas independent
     */
    public SubgraphNode(CompiledGraph<S> graph, BiFunction<S, S, StateUpdate> output) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
        this.output = Objects.requireNonNull(output, "output must not be null");
    }

    @Override public CompletionStage<StateUpdate> execute(S state, NodeRuntime runtime) {
        return graph.invoke(runtime.threadId() + "/" + runtime.nodeId(), state)
                .thenApply(result -> output.apply(state, result.state()));
    }
}
