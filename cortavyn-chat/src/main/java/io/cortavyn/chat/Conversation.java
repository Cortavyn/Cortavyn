package io.cortavyn.chat;

import io.cortavyn.model.api.ChatMessage;
import java.util.List;
import java.util.Objects;

/** Immutable conversation state independent of a model provider. */
public record Conversation(String id, List<ChatMessage> messages) {
    public Conversation {
        Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
    }
}
