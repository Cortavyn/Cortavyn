package io.cortavyn.deep;

import io.cortavyn.chat.Conversation;
import io.cortavyn.graph.GraphNode;
import io.cortavyn.graph.GraphState;
import io.cortavyn.graph.Interrupt;
import io.cortavyn.graph.NodeRuntime;
import io.cortavyn.graph.NodeResult;
import io.cortavyn.graph.StateUpdate;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Bridges a DeepAgent invocation into a durable graph state transition. */
public final class DeepAgentNode implements GraphNode<GraphState> {
    private final DeepAgent agent;
    private final String conversationKey;
    private final String todosKey;
    private final String approvalDecisionsKey;
    private final Function<GraphState, String> input;
    public DeepAgentNode(DeepAgent agent, String conversationKey, String todosKey, Function<GraphState, String> input) { this(agent, conversationKey, todosKey, "deepApprovalDecisions", input); }
    public DeepAgentNode(DeepAgent agent, String conversationKey, String todosKey, String approvalDecisionsKey, Function<GraphState, String> input) { this.agent = Objects.requireNonNull(agent, "agent must not be null"); this.conversationKey = Objects.requireNonNull(conversationKey, "conversationKey must not be null"); this.todosKey = Objects.requireNonNull(todosKey, "todosKey must not be null"); this.approvalDecisionsKey = Objects.requireNonNull(approvalDecisionsKey, "approvalDecisionsKey must not be null"); this.input = Objects.requireNonNull(input, "input must not be null"); }
    @Override public java.util.concurrent.CompletionStage<? extends NodeResult> execute(GraphState state, NodeRuntime runtime) {
        // The caller resumes a graph by putting typed approval decisions into state. On a fresh
        // graph run the key is absent, so the node starts a brand-new DeepAgent invocation.
        Object decisions = state.values().get(approvalDecisionsKey);
        java.util.concurrent.CompletionStage<DeepRun> invocation = decisions == null ? agent.invoke(runtime.threadId(), input.apply(state)) : agent.resume(runtime.threadId(), approvalDecisions(decisions));
        return invocation.thenApply(run -> { Map<String, Object> update = new java.util.LinkedHashMap<>(); update.put(conversationKey, run.conversation()); update.put(todosKey, run.todos()); // Returning Interrupt makes the outer graph checkpoint this exact state.
            if (run.interrupt() == null) return new StateUpdate(update); return new Interrupt(new StateUpdate(update), Map.of("deepInterrupt", run.interrupt(), "approvalDecisionsKey", approvalDecisionsKey)); });
    }
    private static java.util.List<ApprovalDecision> approvalDecisions(Object value) {
        if (!(value instanceof java.util.List<?> values) || !values.stream().allMatch(ApprovalDecision.class::isInstance)) throw new IllegalArgumentException("approval decisions must be a List<ApprovalDecision>");
        return values.stream().map(ApprovalDecision.class::cast).toList();
    }
}
