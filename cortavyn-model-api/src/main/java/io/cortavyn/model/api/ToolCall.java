package io.cortavyn.model.api;
import java.util.Map;
import java.util.Objects;
/** A tool invocation requested by an assistant message. */
public record ToolCall(String id, String name, Map<String, Object> arguments, Map<String, Object> metadata) {
    public ToolCall {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        arguments = Map.copyOf(Objects.requireNonNull(arguments, "arguments must not be null"));
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null"));
    }

    public ToolCall(String id, String name, Map<String, Object> arguments) {
        this(id, name, arguments, Map.of());
    }
}
