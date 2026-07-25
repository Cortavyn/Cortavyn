package io.cortavyn.model.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ModelOperationsTest {
    private static final ChatRequest REQUEST = new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, "Hello")));

    @Test
    void registryAndFactorySelectRegisteredProvider() {
        var registry = new ModelCapabilityRegistry();
        var profile = new ModelProfile("test", "echo", java.util.Set.of(ModelCapability.TOOLS), 8_192);
        registry.register(profile);
        var response = response("ok");
        var factory = new ModelFactory(registry);
        factory.register(new ModelProviderFactory() {
            @Override public String providerId() { return "test"; }
            @Override public ChatModel create(ModelConfiguration configuration) { return ignored -> CompletableFuture.completedFuture(response); }
        });

        assertSame(profile, registry.find("test", "echo").orElseThrow());
        assertEquals(response, factory.create("test", new ModelConfiguration("echo")).complete(REQUEST).toCompletableFuture().join());
    }

    @Test
    void cacheAvoidsSecondProviderCallAndMetricsCaptureUsage() {
        var calls = new AtomicInteger();
        var raw = (ChatModel) request -> { calls.incrementAndGet(); return CompletableFuture.completedFuture(response("cached")); };
        var metrics = new ModelMetrics();
        var model = ChatModels.observed(ChatModels.cached(raw, new InMemoryChatResponseCache()), metrics);

        model.complete(REQUEST).toCompletableFuture().join();
        model.complete(REQUEST).toCompletableFuture().join();

        assertEquals(1, calls.get());
        assertEquals(2, metrics.calls());
        assertEquals(4, metrics.inputTokens());
        assertEquals(6, metrics.outputTokens());
    }

    @Test
    void retriesThenFallsBack() {
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            var attempts = new AtomicInteger();
            ChatModel flaky = request -> attempts.incrementAndGet() == 1
                    ? CompletableFuture.failedFuture(new IllegalStateException("temporary"))
                    : CompletableFuture.completedFuture(response("retry"));
            var retried = new RetryingChatModel(flaky, RetryPolicy.transientFailures(2, Duration.ofMillis(1), Duration.ofMillis(1)), scheduler);
            assertEquals("retry", retried.complete(REQUEST).toCompletableFuture().join().message().content());
            assertEquals(2, attempts.get());

            var fallback = new FallbackChatModel(List.of(request -> CompletableFuture.failedFuture(new IllegalStateException("down")),
                    request -> CompletableFuture.completedFuture(response("fallback"))), failure -> true);
            assertEquals("fallback", fallback.complete(REQUEST).toCompletableFuture().join().message().content());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void batchPreservesRequestOrder() {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var model = new ScriptedChatModel(List.of(CompletableFuture.completedFuture(response("one")), CompletableFuture.completedFuture(response("two"))));
            var results = ChatModels.batch(model, List.of(REQUEST, REQUEST), 1, executor).toCompletableFuture().join();
            assertEquals(List.of("one", "two"), results.stream().map(result -> result.message().content()).toList());
        } finally {
            executor.shutdownNow();
        }
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, text), new ChatResponseMetadata("test", null, "stop", new TokenUsage(2, 3, 5)), Map.of());
    }
}
