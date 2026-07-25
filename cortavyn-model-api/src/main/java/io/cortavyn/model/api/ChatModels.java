package io.cortavyn.model.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

/** Operational helpers for composing portable chat models. */
public final class ChatModels {
    private ChatModels() { }

    /** Invokes all requests while allowing at most {@code maxConcurrency} active calls. */
    public static CompletionStage<List<ChatResponse>> batch(ChatModel model, List<ChatRequest> requests,
                                                             int maxConcurrency, Executor executor) {
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(requests, "requests must not be null");
        if (maxConcurrency <= 0) throw new IllegalArgumentException("maxConcurrency must be positive");
        var bounded = bounded(model, maxConcurrency, executor);
        var results = new ArrayList<CompletableFuture<ChatResponse>>();
        for (var request : requests) results.add(bounded.complete(request).toCompletableFuture());
        return CompletableFuture.allOf(results.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> results.stream().map(CompletableFuture::join).toList());
    }

    /** Decorates a model with a shared asynchronous concurrency limit. */
    public static ChatModel bounded(ChatModel delegate, int maxConcurrency, Executor executor) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        if (maxConcurrency <= 0) throw new IllegalArgumentException("maxConcurrency must be positive");
        var permits = new Semaphore(maxConcurrency);
        return request -> CompletableFuture.supplyAsync(() -> {
            try { permits.acquire(); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while waiting for model capacity", exception); }
            return request;
        }, executor).thenCompose(delegate::complete).whenComplete((response, failure) -> permits.release());
    }

    /** Decorates a model with an in-memory or external cache. */
    public static ChatModel cached(ChatModel delegate, ChatResponseCache cache) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        Objects.requireNonNull(cache, "cache must not be null");
        return request -> cache.get(request).<CompletionStage<ChatResponse>>map(CompletableFuture::completedFuture)
                .orElseGet(() -> delegate.complete(request).thenApply(response -> { cache.put(request, response); return response; }));
    }

    /** Decorates a model with lifecycle observations suitable for tracing and metrics export. */
    public static ChatModel observed(ChatModel delegate, ChatModelObserver observer) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        Objects.requireNonNull(observer, "observer must not be null");
        return request -> {
            long startedAt = System.nanoTime();
            observer.onStart(request);
            try {
                return delegate.complete(request).whenComplete((response, failure) ->
                        observer.onComplete(new ModelCallEvent(request, response, failure, System.nanoTime() - startedAt)));
            } catch (RuntimeException exception) {
                observer.onComplete(new ModelCallEvent(request, null, exception, System.nanoTime() - startedAt));
                throw exception;
            }
        };
    }
}
