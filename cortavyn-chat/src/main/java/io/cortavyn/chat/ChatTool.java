package io.cortavyn.chat;

import io.cortavyn.model.api.ToolDefinition;
import java.util.Objects;

/** An application-owned tool that an agent may expose to a chat model. */
public record ChatTool(ToolDefinition definition, ToolExecutor executor) {
    public ChatTool {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
    }
}
