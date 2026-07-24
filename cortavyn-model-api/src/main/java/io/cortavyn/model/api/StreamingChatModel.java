package io.cortavyn.model.api;
import java.util.concurrent.Flow.Publisher;
/** A chat model that can emit incremental output events. */
public interface StreamingChatModel extends ChatModel { Publisher<ChatStreamEvent> stream(ChatRequest request); }
