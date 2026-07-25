package io.cortavyn.model.api;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Retry decorator that never blocks the caller thread. */
public final class RetryingChatModel implements ChatModel {
    private final ChatModel delegate;
    private final RetryPolicy policy;
    private final ScheduledExecutorService scheduler;
    public RetryingChatModel(ChatModel delegate, RetryPolicy policy, ScheduledExecutorService scheduler) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
    }
    @Override public CompletionStage<ChatResponse> complete(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return attempt(request, 1);
    }
    @SuppressWarnings("FutureReturnValueIgnored")
    private CompletionStage<ChatResponse> attempt(ChatRequest request, int attempt) {
        CompletionStage<ChatResponse> invocation;
        try { invocation = delegate.complete(request); }
        catch (RuntimeException exception) { invocation = CompletableFuture.failedFuture(exception); }
        return invocation.handle((response, failure) -> new Outcome(response, failure)).thenCompose(outcome -> {
            if (outcome.failure == null) return CompletableFuture.completedFuture(outcome.response);
            Throwable failure = unwrap(outcome.failure);
            if (attempt >= policy.maxAttempts() || !policy.retryable().test(failure)) return CompletableFuture.failedFuture(failure);
            var retry = new CompletableFuture<ChatResponse>();
            scheduler.schedule(() -> attempt(request, attempt + 1).whenComplete((response, retryFailure) -> {
                if (retryFailure == null) retry.complete(response); else retry.completeExceptionally(retryFailure);
            }), policy.delayBeforeRetry(attempt).toNanos(), TimeUnit.NANOSECONDS);
            return retry;
        });
    }
    private static Throwable unwrap(Throwable failure) { return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure; }
    private record Outcome(ChatResponse response, Throwable failure) { }
}
