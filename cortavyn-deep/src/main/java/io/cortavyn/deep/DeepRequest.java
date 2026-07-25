package io.cortavyn.deep;
import java.util.Objects;
/** Input for a durable DeepAgent run. */
public record DeepRequest(String threadId, String input) { public DeepRequest { if (threadId == null || threadId.isBlank()) throw new IllegalArgumentException("threadId must not be blank"); Objects.requireNonNull(input, "input must not be null"); } }
