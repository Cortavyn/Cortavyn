package io.cortavyn.chat;

import java.util.Map;
import java.util.Objects;

/** An application-defined progress event emitted while a tool is running. */
public record ToolProgress(String message, Map<String, Object> attributes) {
    public ToolProgress {
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message must not be blank");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes must not be null"));
    }

    public ToolProgress(String message) {
        this(message, Map.of());
    }
}
