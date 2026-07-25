package io.cortavyn.deep;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ToolCall;
import java.util.List;

/** Typed progress and terminal events emitted by a streamed deep-agent invocation. */
public sealed interface DeepEvent permits DeepEvent.Message, DeepEvent.ToolCallRequested, DeepEvent.ToolResult, DeepEvent.TodosUpdated, DeepEvent.ContextOffloaded, DeepEvent.SubagentStarted, DeepEvent.SubagentCompleted, DeepEvent.ApprovalRequested, DeepEvent.Completed, DeepEvent.Interrupted, DeepEvent.Failed {
    record Message(ChatMessage message) implements DeepEvent { }
    record ToolCallRequested(ToolCall call) implements DeepEvent { }
    record ToolResult(ToolCall call, ChatMessage message) implements DeepEvent { }
    record TodosUpdated(List<DeepTodo> todos) implements DeepEvent { public TodosUpdated { todos = List.copyOf(todos); } }
    record ContextOffloaded(String path, int characters) implements DeepEvent { }
    record SubagentStarted(String taskId, String agent) implements DeepEvent { }
    record SubagentCompleted(String taskId, String report) implements DeepEvent { }
    record ApprovalRequested(DeepInterrupt interrupt) implements DeepEvent { }
    record Completed(DeepRun run) implements DeepEvent { }
    record Interrupted(DeepRun run) implements DeepEvent { }
    record Failed(Throwable failure) implements DeepEvent { }
}
