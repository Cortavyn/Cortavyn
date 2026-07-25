package io.cortavyn.chat;

import io.cortavyn.model.api.ToolCall;
import java.util.concurrent.CompletionStage;

/** Executes a tool with its provider call and the runtime context of the current agent run. */
@FunctionalInterface
public interface RuntimeToolExecutor {
    CompletionStage<ToolExecutionResult> execute(ToolCall call, ToolRuntime runtime);
}
