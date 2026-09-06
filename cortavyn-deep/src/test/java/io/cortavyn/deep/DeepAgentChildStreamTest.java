package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.model.api.ChatResponse;
import io.cortavyn.model.api.ToolCall;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DeepAgentChildStreamTest {
    @Test
    void exposesLazyChildMessageAndOutputStreams() throws Exception {
        List<String> output = new CopyOnWriteArrayList<>(); CountDownLatch childDone = new CountDownLatch(1);
        try (DeepAgent agent = DeepAgent.builder(new DelegatingModel()).build()) {
            agent.childStreams().subscribe(new Flow.Subscriber<>() {
                @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                @Override public void onNext(DeepAgentChildStream child) { child.output().subscribe(new CollectingSubscriber<>(output, childDone)); }
                @Override public void onError(Throwable failure) { childDone.countDown(); }
                @Override public void onComplete() { }
            });
            agent.invoke("parent", "delegate").toCompletableFuture().join();
        }
        assertTrue(childDone.await(2, TimeUnit.SECONDS));
        assertEquals(List.of("child"), output);
    }
    private static final class CollectingSubscriber<T> implements Flow.Subscriber<T> {
        private final List<T> values; private final CountDownLatch done;
        CollectingSubscriber(List<T> values, CountDownLatch done) { this.values = values; this.done = done; }
        @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
        @Override public void onNext(T value) { values.add(value); }
        @Override public void onError(Throwable failure) { done.countDown(); }
        @Override public void onComplete() { done.countDown(); }
    }
    private static final class DelegatingModel implements ChatModel {
        private int calls;
        @Override public java.util.concurrent.CompletionStage<ChatResponse> complete(ChatRequest request) { calls++; ChatMessage message = switch (calls) {
            case 1 -> new ChatMessage(ChatMessageRole.ASSISTANT, "", List.of(), null, List.of(new ToolCall("task", "task", Map.of("agent", "general-purpose", "prompt", "work"))), Map.of());
            case 2 -> new ChatMessage(ChatMessageRole.ASSISTANT, "child");
            default -> new ChatMessage(ChatMessageRole.ASSISTANT, "parent");
        }; return CompletableFuture.completedFuture(new ChatResponse(message)); }
    }
}
