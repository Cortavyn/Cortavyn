package io.cortavyn.model.api;
import java.util.Objects;
import java.util.Map;
/** Provider-returned reasoning or thinking content, kept separate from answer text. */
public record ReasoningContent(String text, Map<String, Object> providerState) implements ChatContent {
    public ReasoningContent { Objects.requireNonNull(text, "text must not be null"); providerState = Map.copyOf(Objects.requireNonNull(providerState, "providerState must not be null")); }
    public ReasoningContent(String text) { this(text, Map.of()); }
}
