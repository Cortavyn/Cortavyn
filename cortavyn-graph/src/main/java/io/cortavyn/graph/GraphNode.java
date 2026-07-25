package io.cortavyn.graph;

import java.util.concurrent.CompletionStage;

/** Asynchronous, application-owned graph step. Nodes must not mutate their supplied state. */
@FunctionalInterface
public interface GraphNode<S> { CompletionStage<? extends NodeResult> execute(S state, NodeRuntime runtime); }
