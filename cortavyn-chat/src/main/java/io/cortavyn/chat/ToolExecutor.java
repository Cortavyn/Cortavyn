package io.cortavyn.chat;
import io.cortavyn.model.api.ToolCall;
import java.util.concurrent.CompletionStage;
/** Executes an application-owned tool call; transport adapters never execute tools themselves. */
@FunctionalInterface
public interface ToolExecutor { CompletionStage<ToolExecutionResult> execute(ToolCall call); }
