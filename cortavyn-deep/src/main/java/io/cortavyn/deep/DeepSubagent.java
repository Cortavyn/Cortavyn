package io.cortavyn.deep;

import io.cortavyn.chat.ChatTool;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Declarative specialist available to a parent DeepAgent. */
public record DeepSubagent(String name, String description, String systemPrompt, List<ChatTool> tools, List<WorkspacePermission> workspacePermissions, @Nullable ApprovalPolicy approvalPolicy) {
    public DeepSubagent { if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank"); Objects.requireNonNull(description, "description must not be null"); Objects.requireNonNull(systemPrompt, "systemPrompt must not be null"); tools = List.copyOf(tools); workspacePermissions = List.copyOf(workspacePermissions); }
    public DeepSubagent(String name, String description, String systemPrompt, List<ChatTool> tools) { this(name, description, systemPrompt, tools, List.of(), null); }
}
