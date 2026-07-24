package io.cortavyn.model.api;
import java.util.Objects;
/** Incremental answer text. */
public record ChatTextDelta(String text) implements ChatStreamEvent { public ChatTextDelta { Objects.requireNonNull(text, "text must not be null"); } }
