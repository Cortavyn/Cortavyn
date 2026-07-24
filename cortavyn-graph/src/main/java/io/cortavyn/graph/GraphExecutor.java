package io.cortavyn.graph;

import io.cortavyn.core.AgentRun;
import java.util.concurrent.CompletionStage;

/** Executes a graph and returns its durable run snapshot. */
@FunctionalInterface
public interface GraphExecutor {
    CompletionStage<AgentRun> execute(GraphDefinition definition);
}
