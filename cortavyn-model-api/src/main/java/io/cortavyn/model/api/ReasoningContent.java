package io.cortavyn.model.api;
import java.util.Objects;
/** Provider-returned reasoning or thinking content, kept separate from answer text. */
public record ReasoningContent(String text) implements ChatContent { public ReasoningContent { Objects.requireNonNull(text, "text must not be null"); } }
