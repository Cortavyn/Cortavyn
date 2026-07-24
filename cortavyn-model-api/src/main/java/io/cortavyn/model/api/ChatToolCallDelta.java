package io.cortavyn.model.api;
import java.util.Objects;
/** Incremental tool-call payload, which may arrive in multiple chunks. */
public record ChatToolCallDelta(String id, String name, String argumentsJson) implements ChatStreamEvent { public ChatToolCallDelta { Objects.requireNonNull(id, "id must not be null"); Objects.requireNonNull(name, "name must not be null"); Objects.requireNonNull(argumentsJson, "argumentsJson must not be null"); } }
