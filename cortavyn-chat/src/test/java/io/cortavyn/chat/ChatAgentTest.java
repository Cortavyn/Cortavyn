package io.cortavyn.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatResponse;
import io.cortavyn.model.api.ToolCall;
import io.cortavyn.model.api.ToolDefinition;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChatAgentTest {
    @Test
    void executesToolCallsAndContinuesTheConversation() {
        var model = new ScriptedModel();
        var agent = ChatAgent.builder(model)
                .tools(new ChatTool(new ToolDefinition("weather", "Gets weather.", Map.of()), call -> CompletableFuture.completedFuture(ToolExecutionResult.success("sunny"))))
                .build();

        Conversation result = agent.reply(new Conversation("conversation-1", List.of()), new ChatMessage(ChatMessageRole.USER, "Weather?"))
                .toCompletableFuture().join();

        assertEquals(4, result.messages().size());
        assertEquals(ChatMessageRole.TOOL, result.messages().get(2).role());
        assertEquals("sunny", result.messages().get(2).content());
        assertEquals("It is sunny.", result.messages().get(3).content());
    }

    @Test
    void passesConfiguredRuntimeToRuntimeAwareTools() {
        var observedRuntime = new AtomicReference<ToolRuntime>();
        ToolRuntime runtime = new ToolRuntime("run-42", Map.of("tenant", "acme"), new InMemoryToolStore(), ToolProgressWriter.noop());
        var agent = ChatAgent.builder(new ScriptedModel())
                .runtime(runtime)
                .tools(ChatTool.withRuntime(new ToolDefinition("weather", "Gets weather.", Map.of()), (call, toolRuntime) -> {
                    observedRuntime.set(toolRuntime);
                    return CompletableFuture.completedFuture(ToolExecutionResult.success("sunny"));
                }))
                .build();

        agent.reply(new Conversation("conversation-1", List.of()), new ChatMessage(ChatMessageRole.USER, "Weather?"))
                .toCompletableFuture().join();

        assertEquals(runtime, observedRuntime.get());
    }

    private static final class ScriptedModel implements ChatModel {
        private int invocation;
        @Override public java.util.concurrent.CompletionStage<ChatResponse> complete(io.cortavyn.model.api.ChatRequest request) {
            invocation++;
            ChatMessage message = invocation == 1
                    ? new ChatMessage(ChatMessageRole.ASSISTANT, "", List.of(), null, List.of(new ToolCall("call_1", "weather", Map.of())), Map.of())
                    : new ChatMessage(ChatMessageRole.ASSISTANT, "It is sunny.");
            return CompletableFuture.completedFuture(new ChatResponse(message));
        }
    }
}
