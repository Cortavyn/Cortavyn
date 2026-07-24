package io.cortavyn.model.api;
import java.util.Map;
import java.util.Objects;
/** JSON Schema requested for a structured assistant response. */
public record StructuredOutputSchema(String name, Map<String, Object> jsonSchema, boolean strict) { public StructuredOutputSchema { if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank"); jsonSchema = Map.copyOf(Objects.requireNonNull(jsonSchema, "jsonSchema must not be null")); } }
