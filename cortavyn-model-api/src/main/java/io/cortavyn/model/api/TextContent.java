package io.cortavyn.model.api;
import java.util.Objects;
/** Plain text message content. */
public record TextContent(String text) implements ChatContent { public TextContent { Objects.requireNonNull(text, "text must not be null"); } }
