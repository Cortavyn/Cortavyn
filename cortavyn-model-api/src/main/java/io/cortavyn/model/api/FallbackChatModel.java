package io.cortavyn.model.api;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

/** Tries configured models in order when the failure policy permits a fallback. */
public final class FallbackChatModel implements ChatModel {
    private final List<ChatModel> models;
    private final Predicate<Throwable> fallbackOn;
    public FallbackChatModel(List<? extends ChatModel> models, Predicate<Throwable> fallbackOn) {
        this.models = List.copyOf(Objects.requireNonNull(models, "models must not be null"));
        if (this.models.isEmpty()) throw new IllegalArgumentException("models must not be empty");
        this.fallbackOn = Objects.requireNonNull(fallbackOn, "fallbackOn must not be null");
    }
    @Override public CompletionStage<ChatResponse> complete(ChatRequest request) { return attempt(request, 0); }
    private CompletionStage<ChatResponse> attempt(ChatRequest request, int index) {
        CompletionStage<ChatResponse> call;
        try { call = models.get(index).complete(request); }
        catch (RuntimeException exception) { call = CompletableFuture.failedFuture(exception); }
        return call.handle((response, failure) -> new Outcome(response, failure)).thenCompose(outcome -> {
            if (outcome.failure == null) return CompletableFuture.completedFuture(outcome.response);
            Throwable failure = unwrap(outcome.failure);
            if (index + 1 == models.size() || !fallbackOn.test(failure)) return CompletableFuture.failedFuture(failure);
            return attempt(request, index + 1);
        });
    }
    private static Throwable unwrap(Throwable failure) { return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure; }
    private record Outcome(ChatResponse response, Throwable failure) { }
}
