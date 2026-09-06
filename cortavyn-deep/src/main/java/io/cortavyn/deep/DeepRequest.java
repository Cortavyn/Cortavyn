package io.cortavyn.deep;
import io.cortavyn.model.api.ChatContent;
import io.cortavyn.model.api.TextContent;
import java.util.List;
import java.util.Objects;
/** Input for a durable DeepAgent run. */
public record DeepRequest(String threadId, List<ChatContent> input) {
    public DeepRequest {
        if (threadId == null || threadId.isBlank()) throw new IllegalArgumentException("threadId must not be blank");
        input = List.copyOf(Objects.requireNonNull(input, "input must not be null"));
        if (input.isEmpty()) throw new IllegalArgumentException("input must not be empty");
    }
    /** Compatibility constructor for text-only requests. */
    public DeepRequest(String threadId, String input) { this(threadId, List.of(new TextContent(Objects.requireNonNull(input, "input must not be null")))); }
}
