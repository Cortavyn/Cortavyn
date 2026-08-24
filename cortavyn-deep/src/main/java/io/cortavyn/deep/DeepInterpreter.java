package io.cortavyn.deep;

import java.util.concurrent.CompletionStage;

/** Executes isolated, thread-scoped code for a {@link DeepAgent}. */
public interface DeepInterpreter extends AutoCloseable {
    /** Evaluates code in the session belonging to {@code threadId}. */
    CompletionStage<DeepInterpreterResult> eval(String threadId, String code);

    /** Releases state for one agent thread. */
    default void closeThread(String threadId) { }

    /** Releases all interpreter resources. */
    @Override default void close() { }
}
