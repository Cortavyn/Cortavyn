package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class ContextPolicyTest {
    @Test
    void offloadsLargeToolResultsIntoTheWorkspace() {
        InMemoryWorkspace workspace = new InMemoryWorkspace();
        ChatTool largeResult = ChatTool.typed("large_result", "Returns a large value.", NoArguments.class, ignored -> CompletableFuture.completedFuture(ToolExecutionResult.success("x".repeat(100))));
        DeepRun run = DeepAgent.builder(new ToolThenDoneModel()).tools(largeResult).workspace(workspace).contextPolicy(new ContextPolicy(4, 10, 10_000)).build().invoke("thread-1", "run tool").toCompletableFuture().join();

        assertEquals("done", run.conversation().messages().getLast().content());
        assertEquals("x".repeat(100), workspace.read("context/tool-results/tool-1.txt").toCompletableFuture().join());
        assertTrue(run.conversation().messages().stream().anyMatch(message -> message.content().contains("Large tool result offloaded")));
    }

    @Test
    void summarizesHistoryWithAToolFreeModelCallWhenOverBudget() {
        SummarizingModel model = new SummarizingModel();
        DeepRun run = DeepAgent.builder(model).contextPolicy(new ContextPolicy(4, 100, 1)).build().invoke("thread-2", "answer briefly").toCompletableFuture().join();

        assertEquals("done", run.conversation().messages().getLast().content());
        assertEquals(1, model.summaryCalls);
        assertTrue(run.conversation().messages().getFirst().content().contains("Conversation summary"));
    }

    record NoArguments() { }

    private static final class ToolThenDoneModel implements ChatModel {
        private int calls;
        @Override public java.util.concurrent.CompletionStage<ChatResponse> complete(io.cortavyn.model.api.ChatRequest request) {
            calls++;
            ChatMessage message = calls == 1 ? new ChatMessage(ChatMessageRole.ASSISTANT, "", List.of(), null, List.of(new ToolCall("tool-1", "large_result", Map.of())), Map.of()) : new ChatMessage(ChatMessageRole.ASSISTANT, "done");
            return CompletableFuture.completedFuture(new ChatResponse(message));
        }
    }

    private static final class SummarizingModel implements ChatModel {
        private int summaryCalls;
        @Override public java.util.concurrent.CompletionStage<ChatResponse> complete(io.cortavyn.model.api.ChatRequest request) {
            if (request.tools().isEmpty()) { summaryCalls++; return CompletableFuture.completedFuture(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, "summary"))); }
            return CompletableFuture.completedFuture(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, "done")));
        }
    }
}
