package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.model.api.ChatResponse;
import io.cortavyn.model.api.ToolCall;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class DynamicSubagentTest {
    @Test
    void exposesGeneralPurposeAndCanRegisterAndRunASpecialist() {
        ScriptedModel model = new ScriptedModel();
        try (DeepAgent agent = DeepAgent.builder(model).build()) {
            assertEquals("done", agent.invoke("thread", "delegate").toCompletableFuture().join().conversation().messages().getLast().content());
        }
        assertTrue(java.util.Objects.requireNonNull(model.first).tools().stream().anyMatch(tool -> tool.name().equals("task")));
        assertTrue(java.util.Objects.requireNonNull(model.first).tools().stream().anyMatch(tool -> tool.name().equals("define_specialist")));
    }
    private static final class ScriptedModel implements ChatModel {
        private int calls; private @org.jspecify.annotations.Nullable ChatRequest first;
        @Override public java.util.concurrent.CompletionStage<ChatResponse> complete(ChatRequest request) { calls++; if (calls == 1) first = request; ChatMessage message = switch (calls) {
            case 1 -> new ChatMessage(ChatMessageRole.ASSISTANT, "", List.of(), null, List.of(new ToolCall("define", "define_specialist", Map.of("name", "research", "description", "researches", "systemPrompt", "research"))), Map.of());
            case 2 -> new ChatMessage(ChatMessageRole.ASSISTANT, "", List.of(), null, List.of(new ToolCall("task", "task", Map.of("agent", "research", "prompt", "find facts"))), Map.of());
            case 3 -> new ChatMessage(ChatMessageRole.ASSISTANT, "facts");
            default -> new ChatMessage(ChatMessageRole.ASSISTANT, "done");
        }; return CompletableFuture.completedFuture(new ChatResponse(message)); }
    }
}
