package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cortavyn.chat.ChatTool;
import io.cortavyn.chat.ToolExecutionResult;
import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatResponse;
import io.cortavyn.model.api.ToolCall;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class DeepAgentWorkspaceCheckpointTest {
    @Test
    void restoresDefaultWorkspaceWhenAStoredApprovalResumesInANewAgent() {
        InMemoryDeepRunStore runs = new InMemoryDeepRunStore();
        InMemoryWorkspace prepared = new InMemoryWorkspace();
        ChatTool prepare = ChatTool.typed("prepare", "Writes an intermediate file.", NoArguments.class, ignored -> prepared.write("intermediate.txt", "preserved").thenApply(done -> ToolExecutionResult.success("prepared")));
        DeepAgent first = DeepAgent.builder(new PauseModel()).tools(prepare).workspace(prepared).runStore(runs).build();
        first.invoke("thread-1", "work").toCompletableFuture().join();

        DeepRun resumed = DeepAgent.builder(new FinishModel()).runStore(runs).build().resume("thread-1", List.of(new ApprovalDecision(ApprovalDecision.Type.APPROVE, null, null))).toCompletableFuture().join();

        assertEquals("preserved", resumed.workspace().read("intermediate.txt").toCompletableFuture().join());
        assertEquals("done", resumed.conversation().messages().getLast().content());
    }

    record NoArguments() { }

    private static final class PauseModel implements ChatModel {
        @Override public java.util.concurrent.CompletionStage<ChatResponse> complete(io.cortavyn.model.api.ChatRequest request) {
            return CompletableFuture.completedFuture(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, "", List.of(), null, List.of(new ToolCall("prepare", "prepare", Map.of()), new ToolCall("write", "write_file", Map.of("path", "approved.txt", "content", "yes"))), Map.of())));
        }
    }
    private static final class FinishModel implements ChatModel {
        @Override public java.util.concurrent.CompletionStage<ChatResponse> complete(io.cortavyn.model.api.ChatRequest request) { return CompletableFuture.completedFuture(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, "done"))); }
    }
}
