package io.cortavyn.deep;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ToolCall;
import java.util.List;

/** Checkpointable continuation state captured before reviewed tool calls execute. */
public record DeepPendingRun(String threadId, List<ChatMessage> messages, List<ToolCall> calls, int iteration, @org.jspecify.annotations.Nullable WorkspaceSnapshot workspaceSnapshot) {
    public DeepPendingRun { if (threadId == null || threadId.isBlank()) throw new IllegalArgumentException("threadId must not be blank"); messages = List.copyOf(messages); calls = List.copyOf(calls); if (iteration < 0) throw new IllegalArgumentException("iteration must not be negative"); }
    public DeepPendingRun(String threadId, List<ChatMessage> messages, List<ToolCall> calls, int iteration) { this(threadId, messages, calls, iteration, null); }
}
