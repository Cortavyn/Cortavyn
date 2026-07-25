package io.cortavyn.chat;

import io.cortavyn.graph.GraphNode;
import io.cortavyn.graph.GraphState;
import io.cortavyn.graph.NodeRuntime;
import io.cortavyn.graph.StateUpdate;
import io.cortavyn.model.api.ChatMessage;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/** Bridges a graph step to a {@link ChatSession} without coupling the graph module to chat. */
public final class ChatAgentNode implements GraphNode<GraphState> {
    private final Function<NodeRuntime, ChatSession> sessions;
    private final String conversationKey;
    private final Function<GraphState, ChatMessage> message;

    /**
     * @param sessions creates a run-scoped session; use the runtime run ID to configure {@link ToolRuntime}
     * @param conversationKey state key containing and receiving the immutable conversation
     * @param message maps current graph state to the next user message
     */
    public ChatAgentNode(Function<NodeRuntime, ChatSession> sessions, String conversationKey, Function<GraphState, ChatMessage> message) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.conversationKey = Objects.requireNonNull(conversationKey, "conversationKey must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
    }

    @Override public CompletionStage<StateUpdate> execute(GraphState state, NodeRuntime runtime) {
        // Conversations stay in graph state so checkpoints include the exact model/tool history.
        Conversation conversation = state.get(conversationKey, Conversation.class);
        // The factory receives the graph run context and can create a matching ToolRuntime.
        return sessions.apply(runtime).reply(conversation, message.apply(state))
                .thenApply(updated -> new StateUpdate(Map.of(conversationKey, updated)));
    }
}
