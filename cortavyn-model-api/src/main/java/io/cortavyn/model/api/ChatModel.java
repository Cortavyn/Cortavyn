package io.cortavyn.model.api;

import java.util.concurrent.CompletionStage;

/** An asynchronous, provider-neutral chat-model contract. */
@FunctionalInterface
public interface ChatModel {
    /** @param type record type that describes the requested response JSON */
    default <T> StructuredChatModel<T> withStructuredOutput(Class<T> type) { return new StructuredChatModel<>(this, type); }
    /**
     * Requests one assistant response.
     *
     * @param request immutable messages, tools, generation parameters, and optional extensions
     * @return a stage completing with one normalized assistant response or failing with a provider error
     */
    CompletionStage<ChatResponse> complete(ChatRequest request);
}
