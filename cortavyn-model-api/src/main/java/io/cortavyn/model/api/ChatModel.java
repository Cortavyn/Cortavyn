package io.cortavyn.model.api;

import java.util.concurrent.CompletionStage;

/** An asynchronous, provider-neutral chat-model contract. */
@FunctionalInterface
public interface ChatModel {
    CompletionStage<ChatResponse> complete(ChatRequest request);
}
