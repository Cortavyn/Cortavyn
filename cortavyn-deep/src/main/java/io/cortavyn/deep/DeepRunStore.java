package io.cortavyn.deep;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Durable storage boundary for paused deep-agent runs. */
public interface DeepRunStore {
    CompletionStage<Void> save(DeepPendingRun run);
    CompletionStage<Optional<DeepPendingRun>> get(String threadId);
    CompletionStage<Void> delete(String threadId);
    /** Retrieves and removes a paused run; prefer get/delete when validation must precede deletion. */
    default CompletionStage<Optional<DeepPendingRun>> remove(String threadId) { return get(threadId).thenCompose(found -> delete(threadId).thenApply(ignored -> found)); }
    static DeepRunStore inMemory() { return new InMemoryDeepRunStore(); }
}
