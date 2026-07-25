package io.cortavyn.deep;
import java.util.Objects;
/** A durable unit of agent-owned work. */
public record DeepTodo(String id, String content, Status status) { public DeepTodo { if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank"); Objects.requireNonNull(content, "content must not be null"); Objects.requireNonNull(status, "status must not be null"); } public enum Status { PENDING, IN_PROGRESS, COMPLETED } }
