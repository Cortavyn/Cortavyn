package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.cortavyn.graph.GraphState;
import io.cortavyn.graph.GraphStatus;
import io.cortavyn.graph.StateChannel;
import io.cortavyn.graph.StateGraph;
import io.cortavyn.graph.StateSchema;
import io.cortavyn.graph.StateUpdate;
import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatResponse;
import io.cortavyn.model.api.ToolCall;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class DeepAgentNodeTest {
    @Test
    void graphCheckpointPausesForApprovalAndResumesTheSameDeepRun() {
        InMemoryWorkspace workspace = new InMemoryWorkspace();
        DeepAgent agent = DeepAgent.builder(new ScriptedModel()).workspace(workspace).build();
        StateSchema<GraphState> schema = StateSchema.builder(GraphState.adapter())
                .channel("input", StateChannel.lastValue()).channel("conversation", StateChannel.lastValue())
                .channel("todos", StateChannel.lastValue()).channel("deepApprovalDecisions", StateChannel.lastValue()).build();
        var graph = new StateGraph<>(schema).addNode("agent", new DeepAgentNode(agent, "conversation", "todos", state -> state.get("input", String.class)))
                .addEdge(StateGraph.START, "agent").addEdge("agent", StateGraph.END).compile();

        var paused = graph.invoke("thread-1", new GraphState(Map.of("input", "write"))).toCompletableFuture().join();

        assertEquals(GraphStatus.INTERRUPTED, paused.status());
        assertNotNull(paused.resumeToken());
        assertNotNull(graph.history("thread-1").getLast().interruptPayload());
        Map<String, Object> payload = java.util.Objects.requireNonNull(graph.history("thread-1").getLast().interruptPayload());
        DeepInterrupt interrupt = assertInstanceOf(DeepInterrupt.class, payload.get("deepInterrupt"));
        assertEquals("write_file", interrupt.actions().getFirst().toolName());
        var completed = graph.resume(paused.resumeToken(), new StateUpdate(Map.of("deepApprovalDecisions", List.of(new ApprovalDecision(ApprovalDecision.Type.APPROVE, null, null))))).toCompletableFuture().join();
        assertEquals(GraphStatus.SUCCEEDED, completed.status());
        assertEquals("done", completed.state().get("conversation", io.cortavyn.chat.Conversation.class).messages().getLast().content());
        assertEquals("hello", workspace.read("note.txt").toCompletableFuture().join());
    }

    private static final class ScriptedModel implements ChatModel {
        private int calls;
        @Override public java.util.concurrent.CompletionStage<ChatResponse> complete(io.cortavyn.model.api.ChatRequest request) {
            calls++;
            ChatMessage message = calls == 1 ? new ChatMessage(ChatMessageRole.ASSISTANT, "", List.of(), null, List.of(new ToolCall("write-1", "write_file", Map.of("path", "note.txt", "content", "hello"))), Map.of()) : new ChatMessage(ChatMessageRole.ASSISTANT, "done");
            return CompletableFuture.completedFuture(new ChatResponse(message));
        }
    }
}
