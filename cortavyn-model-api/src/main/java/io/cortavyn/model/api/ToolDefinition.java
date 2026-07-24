package io.cortavyn.model.api;
import java.util.Map;
import java.util.Objects;
/** A callable tool exposed to a chat model via a JSON Schema input contract. */
public record ToolDefinition(String name, String description, Map<String, Object> inputSchema) { public ToolDefinition { if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank"); Objects.requireNonNull(description, "description must not be null"); inputSchema = Map.copyOf(Objects.requireNonNull(inputSchema, "inputSchema must not be null")); } }
