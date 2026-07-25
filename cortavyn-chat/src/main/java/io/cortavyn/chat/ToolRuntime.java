package io.cortavyn.chat;

import java.util.Map;
import java.util.Objects;

/** Per-run context exposed to runtime-aware tools. */
public record ToolRuntime(String runId, Map<String, Object> userContext, ToolStore store, ToolProgressWriter progressWriter) {
    public ToolRuntime {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId must not be blank");
        userContext = Map.copyOf(Objects.requireNonNull(userContext, "userContext must not be null"));
        Objects.requireNonNull(store, "store must not be null");
        Objects.requireNonNull(progressWriter, "progressWriter must not be null");
    }

    public static ToolRuntime ephemeral(String runId) {
        return new ToolRuntime(runId, Map.of(), new InMemoryToolStore(), ToolProgressWriter.noop());
    }
}
