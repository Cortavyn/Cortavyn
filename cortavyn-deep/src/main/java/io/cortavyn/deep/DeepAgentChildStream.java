package io.cortavyn.deep;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ToolCall;
import java.util.concurrent.Flow;

/** Lazy, separately subscribable event streams for one delegated child-agent run. */
public record DeepAgentChildStream(String taskId, String agent, Flow.Publisher<DeepEvent> events, Flow.Publisher<DeepAgentChildStream> children) {
    public Flow.Publisher<ChatMessage> messages() { return filter(events, DeepEvent.Message.class, DeepEvent.Message::message); }
    public Flow.Publisher<ToolCall> toolCalls() { return filter(events, DeepEvent.ToolCallRequested.class, DeepEvent.ToolCallRequested::call); }
    public Flow.Publisher<String> output() { return subscriber -> events.subscribe(new Flow.Subscriber<>() {
        @Override public void onSubscribe(Flow.Subscription subscription) { subscriber.onSubscribe(subscription); }
        @Override public void onNext(DeepEvent event) { if (event instanceof DeepEvent.TextDelta delta) subscriber.onNext(delta.text()); else if (event instanceof DeepEvent.Message message && message.message().role() == io.cortavyn.model.api.ChatMessageRole.ASSISTANT && !message.message().content().isEmpty()) subscriber.onNext(message.message().content()); }
        @Override public void onError(Throwable failure) { subscriber.onError(failure); }
        @Override public void onComplete() { subscriber.onComplete(); }
    }); }
    private static <E extends DeepEvent, T> Flow.Publisher<T> filter(Flow.Publisher<DeepEvent> source, Class<E> type, java.util.function.Function<E, T> mapper) {
        return subscriber -> source.subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscriber.onSubscribe(subscription); }
            @Override public void onNext(DeepEvent event) { if (type.isInstance(event)) subscriber.onNext(mapper.apply(type.cast(event))); }
            @Override public void onError(Throwable failure) { subscriber.onError(failure); }
            @Override public void onComplete() { subscriber.onComplete(); }
        });
    }
}
