package io.cortavyn.model.api;

import java.util.concurrent.CompletionStage;

/** An asynchronous, provider-neutral chat-model contract. */
@FunctionalInterface
public interface ChatModel {
    /**
     * Requests one assistant response.
     *
     * @param request immutable messages, tools, generation parameters, and optional extensions
     * @return a stage completing with one normalized assistant response or failing with a provider error
     */
    CompletionStage<ChatResponse> complete(ChatRequest request);
}
