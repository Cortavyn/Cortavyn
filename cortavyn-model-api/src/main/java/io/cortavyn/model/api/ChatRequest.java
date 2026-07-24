package io.cortavyn.model.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A provider-neutral request to a chat model. */
public record ChatRequest(List<ChatMessage> messages, List<ToolDefinition> tools, ChatGenerationParameters parameters, Map<String, Object> extensions) {
    public ChatRequest {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        tools = List.copyOf(Objects.requireNonNull(tools, "tools must not be null"));
        Objects.requireNonNull(parameters, "parameters must not be null");
        extensions = Map.copyOf(Objects.requireNonNull(extensions, "extensions must not be null"));
    }
    public ChatRequest(List<ChatMessage> messages) { this(messages, List.of(), ChatGenerationParameters.defaults(), Map.of()); }
}
