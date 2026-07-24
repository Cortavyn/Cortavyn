package io.cortavyn.model.api;

import java.util.Map;
import java.util.Objects;

/** The normalized result returned by a chat model. */
public record ChatResponse(ChatMessage message, ChatResponseMetadata metadata, Map<String, Object> extensions) {
    public ChatResponse {
        Objects.requireNonNull(message, "message must not be null");
        if (message.role() != ChatMessageRole.ASSISTANT) {
            throw new IllegalArgumentException("response message must have the ASSISTANT role");
        }
        Objects.requireNonNull(metadata, "metadata must not be null");
        extensions = Map.copyOf(Objects.requireNonNull(extensions, "extensions must not be null"));
    }
    public ChatResponse(ChatMessage message) { this(message, ChatResponseMetadata.empty(), Map.of()); }
}
