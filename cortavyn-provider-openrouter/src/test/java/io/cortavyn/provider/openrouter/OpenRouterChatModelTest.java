package io.cortavyn.provider.openrouter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenRouterChatModelTest {
    @Test
    void mapsTheOpenRouterChatCompletionsRequest() {
        var model = OpenRouterChatModel.builder()
                .apiKey("test-key")
                .modelName("openai/gpt-chat-latest")
                .temperature(0.7)
                .maxTokens(64)
                .topP(0.9)
                .stopSequences(List.of("STOP"))
                .providerPreferences(Map.of("order", List.of("OpenAI")))
                .route("fallback")
                .reasoning(Map.of("effort", "high"))
                .plugins(List.of(Map.of("id", "web")))
                .sessionId("conversation-1")
                .trace(Map.of("trace_id", "trace-1"))
                .build();

        String payload = model.toRequestJson(new ChatRequest(List.of(
                new ChatMessage(ChatMessageRole.SYSTEM, "Be concise."),
                new ChatMessage(ChatMessageRole.USER, "Hello"))));

        assertEquals("{\"model\":\"openai/gpt-chat-latest\",\"temperature\":0.7,\"max_tokens\":64,\"top_p\":0.9,\"stop\":[\"STOP\"],\"provider\":{\"order\":[\"OpenAI\"]},\"route\":\"fallback\",\"reasoning\":{\"effort\":\"high\"},\"plugins\":[{\"id\":\"web\"}],\"session_id\":\"conversation-1\",\"trace\":{\"trace_id\":\"trace-1\"},\"messages\":[{\"role\":\"system\",\"content\":\"Be concise.\"},{\"role\":\"user\",\"content\":\"Hello\"}]}", payload);
    }

    @Test
    void validatesGenerationParameters() {
        assertThrows(IllegalArgumentException.class, () -> OpenRouterChatModel.builder().apiKey("test-key").temperature(2.1).build());
        assertThrows(IllegalArgumentException.class, () -> OpenRouterChatModel.builder().apiKey("test-key").maxTokens(0).build());
    }

    @Test
    void keepsToolMessagesOutUntilPortableToolCallIdsExist() {
        var model = OpenRouterChatModel.builder().apiKey("test-key").build();
        var request = new ChatRequest(List.of(new ChatMessage(ChatMessageRole.TOOL, "result")));
        assertThrows(IllegalArgumentException.class, () -> model.toRequestJson(request));
    }
}
