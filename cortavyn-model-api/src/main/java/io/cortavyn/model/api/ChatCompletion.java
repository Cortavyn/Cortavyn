package io.cortavyn.model.api;
import java.util.Objects;
/** Terminal stream event containing the fully normalized response. */
public record ChatCompletion(ChatResponse response) implements ChatStreamEvent { public ChatCompletion { Objects.requireNonNull(response, "response must not be null"); } }
