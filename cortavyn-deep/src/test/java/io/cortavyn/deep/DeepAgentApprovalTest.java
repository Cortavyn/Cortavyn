package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatResponse;
import io.cortavyn.model.api.ToolCall;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class DeepAgentApprovalTest {
    @Test
    void pausesBeforeWriteAndResumesAfterApproval() {
        var workspace = new InMemoryWorkspace();
        var agent = DeepAgent.builder(new ScriptedModel()).workspace(workspace).build();
        DeepRun paused = agent.invoke("thread-1", "write a note").toCompletableFuture().join();
        assertNotNull(paused.interrupt());
        assertEquals(io.cortavyn.graph.GraphStatus.INTERRUPTED, paused.graphStatus());
        assertEquals(io.cortavyn.graph.GraphStatus.INTERRUPTED, agent.history("thread-1").getLast().status());
        assertEquals("write_file", paused.interrupt().actions().getFirst().toolName());
        DeepRun completed = agent.resume("thread-1", List.of(new ApprovalDecision(ApprovalDecision.Type.APPROVE, null, null))).toCompletableFuture().join();
        assertEquals("done", completed.conversation().messages().getLast().content());
        assertEquals(io.cortavyn.graph.GraphStatus.SUCCEEDED, completed.graphStatus());
        assertEquals("hello", workspace.read("note.txt").toCompletableFuture().join());
    }
    @Test
    void keepsThePendingRunWhenResumeDoesNotContainEveryDecision() {
        var agent = DeepAgent.builder(new ScriptedModel()).approvalPolicy(ApprovalPolicy.builder().require("write_file", ApprovalDecision.Type.REJECT).build()).build();
        agent.invoke("thread-2", "write a note").toCompletableFuture().join();

        assertThrows(java.util.concurrent.CompletionException.class, () -> agent.resume("thread-2", List.of()).toCompletableFuture().join());
        assertEquals("done", agent.resume("thread-2", List.of(new ApprovalDecision(ApprovalDecision.Type.REJECT, null, "no"))).toCompletableFuture().join().conversation().messages().getLast().content());
    }
    @Test
    void validatesEditedArgumentsBeforeRunningTheSensitiveTool() {
        InMemoryWorkspace workspace = new InMemoryWorkspace();
        DeepAgent agent = DeepAgent.builder(new ScriptedModel()).workspace(workspace).build();
        agent.invoke("thread-3", "write a note").toCompletableFuture().join();

        DeepRun finished = agent.resume("thread-3", List.of(new ApprovalDecision(ApprovalDecision.Type.EDIT, Map.of("path", "note.txt"), null))).toCompletableFuture().join();

        assertEquals("done", finished.conversation().messages().getLast().content());
        assertEquals("Approval edit rejected: arguments.content is required", finished.conversation().messages().get(finished.conversation().messages().size() - 2).content());
        assertThrows(java.util.concurrent.CompletionException.class, () -> workspace.read("note.txt").toCompletableFuture().join());
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
