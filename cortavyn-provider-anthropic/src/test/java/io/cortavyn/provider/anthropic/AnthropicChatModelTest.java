package io.cortavyn.provider.anthropic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnthropicChatModelTest {
    @Test void separatesSystemInstructionsAndMapsGenerationParameters() {
        var model = AnthropicChatModel.builder().apiKey("test-key").modelName("claude-test").maxTokens(64)
                .temperature(0.7).topK(40).topP(0.9).stopSequences(List.of("STOP"))
                .additionalParameters(Map.of("thinking", Map.of("type", "adaptive"))).build();
        String payload = model.toRequestJson(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.SYSTEM, "Be concise."), new ChatMessage(ChatMessageRole.USER, "Hello"))));
        assertEquals("{\"model\":\"claude-test\",\"max_tokens\":64,\"temperature\":0.7,\"top_k\":40,\"top_p\":0.9,\"stop_sequences\":[\"STOP\"],\"thinking\":{\"type\":\"adaptive\"},\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}],\"system\":\"Be concise.\"}", payload);
    }

    @Test void validatesAnthropicParameters() {
        assertThrows(IllegalArgumentException.class, () -> AnthropicChatModel.builder().apiKey("key").maxTokens(0).build());
        assertThrows(IllegalArgumentException.class, () -> AnthropicChatModel.builder().apiKey("key").topP(1.1).build());
    }

    @Test void rejectsToolMessagesUntilPortableToolUseExists() {
        var model = AnthropicChatModel.builder().apiKey("key").build();
        assertThrows(IllegalArgumentException.class, () -> model.toRequestJson(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.TOOL, "result")))));
    }
}
