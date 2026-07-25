package io.cortavyn.deep;

import java.util.concurrent.CompletionStage;

/** Long-lived, caller-scoped instructions loaded at the beginning of a run. */
public interface DeepMemory {
    CompletionStage<String> load(String namespace);
    CompletionStage<Void> save(String namespace, String content);
    static DeepMemory none() { return new DeepMemory() { @Override public CompletionStage<String> load(String namespace) { return java.util.concurrent.CompletableFuture.completedFuture(""); } @Override public CompletionStage<Void> save(String namespace, String content) { return java.util.concurrent.CompletableFuture.completedFuture(null); } }; }
}
