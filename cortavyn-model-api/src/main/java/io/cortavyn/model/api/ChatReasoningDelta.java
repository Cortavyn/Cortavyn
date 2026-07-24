package io.cortavyn.model.api;
import java.util.Objects;
/** Incremental reasoning content. */
public record ChatReasoningDelta(String text) implements ChatStreamEvent { public ChatReasoningDelta { Objects.requireNonNull(text, "text must not be null"); } }
