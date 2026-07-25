package io.cortavyn.chat;

import java.util.concurrent.CompletionStage;

/** Executes a tool after its provider arguments have been converted to the declared Java type. */
@FunctionalInterface
public interface TypedToolExecutor<T> {
    CompletionStage<ToolExecutionResult> execute(T arguments);
}
