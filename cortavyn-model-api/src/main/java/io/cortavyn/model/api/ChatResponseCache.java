package io.cortavyn.model.api;

import java.util.Optional;

/** Cache boundary for deterministic or application-approved chat responses. */
public interface ChatResponseCache {
    Optional<ChatResponse> get(ChatRequest request);
    void put(ChatRequest request, ChatResponse response);
}
