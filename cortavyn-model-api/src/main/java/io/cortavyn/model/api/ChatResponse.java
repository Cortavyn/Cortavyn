package io.cortavyn.model.api;

import java.util.Objects;

/** The normalized result returned by a chat model. */
public record ChatResponse(ChatMessage message) {
    public ChatResponse {
        Objects.requireNonNull(message, "message must not be null");
        if (message.role() != ChatMessageRole.ASSISTANT) {
            throw new IllegalArgumentException("response message must have the ASSISTANT role");
        }
    }
}
