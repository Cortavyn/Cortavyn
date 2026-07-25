package io.cortavyn.chat;

import java.util.concurrent.CompletionStage;

/** Executes a typed tool with the runtime context of the current agent run. */
@FunctionalInterface
public interface RuntimeTypedToolExecutor<T> {
    CompletionStage<ToolExecutionResult> execute(T arguments, ToolRuntime runtime);
}
