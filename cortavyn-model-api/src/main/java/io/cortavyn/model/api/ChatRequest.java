package io.cortavyn.model.api;

import java.util.List;
import java.util.Objects;

/** A provider-neutral request to a chat model. */
public record ChatRequest(List<ChatMessage> messages) {
    public ChatRequest {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
    }
}
