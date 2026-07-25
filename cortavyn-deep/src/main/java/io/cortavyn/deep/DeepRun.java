package io.cortavyn.deep;
import io.cortavyn.chat.Conversation;
import io.cortavyn.graph.GraphStatus;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
/** Result and durable state visible after a DeepAgent invocation. */
public record DeepRun(String threadId, Conversation conversation, DeepWorkspace workspace, List<DeepTodo> todos, @Nullable DeepInterrupt interrupt, GraphStatus graphStatus) {
    public DeepRun { Objects.requireNonNull(threadId, "threadId must not be null"); Objects.requireNonNull(conversation, "conversation must not be null"); Objects.requireNonNull(workspace, "workspace must not be null"); Objects.requireNonNull(graphStatus, "graphStatus must not be null"); todos = List.copyOf(todos); }
    public DeepRun(String threadId, Conversation conversation, DeepWorkspace workspace, List<DeepTodo> todos, @Nullable DeepInterrupt interrupt) { this(threadId, conversation, workspace, todos, interrupt, interrupt == null ? GraphStatus.SUCCEEDED : GraphStatus.INTERRUPTED); }
}
