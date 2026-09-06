package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class DeepAgentInterpreterTest {
    @Test
    void exposesEvalOnlyWhenAnInterpreterIsConfigured() {
        ToolCheckingModel withoutInterpreter = new ToolCheckingModel(false);
        DeepAgent.builder(withoutInterpreter).build().invoke("thread", "hello").toCompletableFuture().join();

        try (DeepAgent agent = DeepAgent.builder(new EvalThenDoneModel()).interpreter(new QuickJsInterpreter()).build()) {
            DeepRun run = agent.invoke("thread", "calculate").toCompletableFuture().join();
            assertEquals("done", run.conversation().messages().getLast().content());
        }
    }

    private static final class ToolCheckingModel implements ChatModel {
        private final boolean expectsEval;
        private ToolCheckingModel(boolean expectsEval) { this.expectsEval = expectsEval; }
        @Override public CompletionStage<ChatResponse> complete(ChatRequest request) {
            boolean containsEval = request.tools().stream().anyMatch(tool -> tool.name().equals("eval"));
            if (expectsEval) assertTrue(containsEval); else assertFalse(containsEval);
            return CompletableFuture.completedFuture(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, "done")));
        }
    }

    private static final class EvalThenDoneModel implements ChatModel {
        private int calls;
        @Override public CompletionStage<ChatResponse> complete(ChatRequest request) {
            calls++;
            if (calls == 1) {
                assertTrue(request.tools().stream().anyMatch(tool -> tool.name().equals("eval")));
                return CompletableFuture.completedFuture(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, "", List.of(), null,
                        List.of(new ToolCall("eval-1", "eval", Map.of("code", "console.log('computed'); 6 * 7"))), Map.of())));
            }
            assertTrue(request.messages().getLast().content().contains("computed"));
            assertTrue(request.messages().getLast().content().contains("\"value\":42"));
            return CompletableFuture.completedFuture(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, "done")));
        }
    }
}
