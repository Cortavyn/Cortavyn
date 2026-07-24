package io.cortavyn.model.api;

import java.util.Objects;

/** A provider-neutral chat message. */
public record ChatMessage(ChatMessageRole role, String content) {
    public ChatMessage {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}
