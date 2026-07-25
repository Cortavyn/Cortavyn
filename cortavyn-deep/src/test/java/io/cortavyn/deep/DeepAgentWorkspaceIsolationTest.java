package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatResponse;
import io.cortavyn.model.api.ToolCall;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class DeepAgentWorkspaceIsolationTest {
    @Test
    void givesEachDefaultWorkspaceThreadItsOwnFiles() {
        DeepAgent agent = DeepAgent.builder(new WriteInputModel()).approvalPolicy(ApprovalPolicy.none()).build();
        DeepRun first = agent.invoke("first", "alpha").toCompletableFuture().join();
        DeepRun second = agent.invoke("second", "beta").toCompletableFuture().join();

        assertEquals("alpha", first.workspace().read("notes/value.txt").toCompletableFuture().join());
        assertEquals("beta", second.workspace().read("notes/value.txt").toCompletableFuture().join());
        assertThrows(java.util.concurrent.CompletionException.class, () -> first.workspace().read("missing.txt").toCompletableFuture().join());
    }

    private static final class WriteInputModel implements ChatModel {
        @Override public java.util.concurrent.CompletionStage<ChatResponse> complete(io.cortavyn.model.api.ChatRequest request) {
            if (request.messages().stream().anyMatch(message -> message.role() == ChatMessageRole.TOOL)) return CompletableFuture.completedFuture(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, "done")));
            String input = request.messages().stream().filter(message -> message.role() == ChatMessageRole.USER).findFirst().orElseThrow().content();
            return CompletableFuture.completedFuture(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, "", List.of(), null, List.of(new ToolCall("write", "write_file", Map.of("path", "notes/value.txt", "content", input))), Map.of())));
        }
    }
}
