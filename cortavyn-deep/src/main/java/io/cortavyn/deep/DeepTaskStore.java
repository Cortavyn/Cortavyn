package io.cortavyn.deep;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Durable application boundary for asynchronous subagent task state. */
public interface DeepTaskStore {
    CompletionStage<Void> save(DeepSubagentTask task);
    CompletionStage<Optional<DeepSubagentTask>> get(String id);
    static DeepTaskStore inMemory() { return new InMemoryDeepTaskStore(); }
}
