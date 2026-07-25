package io.cortavyn.model.api;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Deterministic FIFO fake model for unit and end-to-end conversation tests. */
public final class ScriptedChatModel implements ChatModel {
    private final Queue<CompletionStage<ChatResponse>> outcomes;
    private final List<ChatRequest> requests = new java.util.ArrayList<>();
    public ScriptedChatModel(List<? extends CompletionStage<ChatResponse>> outcomes) {
        this.outcomes = new ArrayDeque<>(Objects.requireNonNull(outcomes, "outcomes must not be null"));
    }
    public static ScriptedChatModel responses(ChatResponse... responses) {
        return new ScriptedChatModel(java.util.Arrays.stream(responses).map(CompletableFuture::completedFuture).toList());
    }
    @Override public synchronized CompletionStage<ChatResponse> complete(ChatRequest request) {
        requests.add(Objects.requireNonNull(request, "request must not be null"));
        var outcome = outcomes.poll();
        return outcome == null ? CompletableFuture.failedFuture(new IllegalStateException("No scripted model outcome remains")) : outcome;
    }
    public synchronized List<ChatRequest> requests() { return List.copyOf(requests); }
    public synchronized int remainingOutcomes() { return outcomes.size(); }
}
