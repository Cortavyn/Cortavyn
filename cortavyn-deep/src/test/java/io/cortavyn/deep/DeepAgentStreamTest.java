package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
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

class DeepAgentStreamTest {
    @Test
    void emitsModelAndToolProgressBeforeCompletion() throws InterruptedException {
        DeepAgent agent = DeepAgent.builder(new ScriptedModel()).build();
        List<DeepEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch completed = new CountDownLatch(1);
        agent.stream(new DeepRequest("thread-1", "list files")).subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(DeepEvent event) { events.add(event); }
            @Override public void onError(Throwable failure) { completed.countDown(); }
            @Override public void onComplete() { completed.countDown(); }
        });

        assertTrue(completed.await(2, TimeUnit.SECONDS));
        assertEquals(List.of(DeepEvent.Message.class, DeepEvent.ToolCallRequested.class, DeepEvent.ToolResult.class, DeepEvent.Message.class, DeepEvent.Completed.class), events.stream().map(Object::getClass).toList());
    }

    @Test
    void forwardsTextDeltasFromStreamingModels() throws InterruptedException {
        DeepAgent agent = DeepAgent.builder(new StreamingScriptedModel()).build();
        List<DeepEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch completed = new CountDownLatch(1);
        agent.stream(new DeepRequest("thread-1", "say hello")).subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(DeepEvent event) { events.add(event); }
            @Override public void onError(Throwable failure) { completed.countDown(); }
            @Override public void onComplete() { completed.countDown(); }
        });

        assertTrue(completed.await(2, TimeUnit.SECONDS));
        assertEquals(List.of(DeepEvent.TextDelta.class, DeepEvent.TextDelta.class, DeepEvent.Message.class, DeepEvent.Completed.class),
                events.stream().map(Object::getClass).toList());
    }

    private static final class ScriptedModel implements ChatModel {
        private int calls;
        @Override public java.util.concurrent.CompletionStage<ChatResponse> complete(io.cortavyn.model.api.ChatRequest request) {
            calls++;
            ChatMessage message = calls == 1 ? new ChatMessage(ChatMessageRole.ASSISTANT, "", List.of(), null, List.of(new ToolCall("list-1", "ls", Map.of("path", "."))), Map.of()) : new ChatMessage(ChatMessageRole.ASSISTANT, "done");
            return CompletableFuture.completedFuture(new ChatResponse(message));
        }
    }

    private static final class StreamingScriptedModel implements io.cortavyn.model.api.StreamingChatModel {
        @Override public java.util.concurrent.CompletionStage<ChatResponse> complete(io.cortavyn.model.api.ChatRequest request) {
            return CompletableFuture.failedStage(new AssertionError("streaming path expected"));
        }
        @Override public java.util.concurrent.Flow.Publisher<io.cortavyn.model.api.ChatStreamEvent> stream(io.cortavyn.model.api.ChatRequest request) {
            return subscriber -> {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override public void request(long count) { }
                    @Override public void cancel() { }
                });
                subscriber.onNext(new io.cortavyn.model.api.ChatTextDelta("hel"));
                subscriber.onNext(new io.cortavyn.model.api.ChatTextDelta("lo"));
                subscriber.onNext(new io.cortavyn.model.api.ChatCompletion(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, "hello"))));
                subscriber.onComplete();
            };
        }
    }
}
