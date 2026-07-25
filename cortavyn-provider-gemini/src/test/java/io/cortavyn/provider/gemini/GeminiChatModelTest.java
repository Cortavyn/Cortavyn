package io.cortavyn.provider.gemini;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.model.api.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeminiChatModelTest {
    @Test
    void mapsSystemInstructionsAndConversationRolesLikeLangChain() {
        var model = GeminiChatModel.builder()
                .apiKey("test-key")
                .temperature(0.7)
                .topP(0.9)
                .topK(40)
                .maxOutputTokens(64)
                .stopSequences(List.of("STOP"))
                .build();

        String payload = model.toRequestJson(new ChatRequest(List.of(
                new ChatMessage(ChatMessageRole.SYSTEM, "Be concise."),
                new ChatMessage(ChatMessageRole.USER, "Hello"),
                new ChatMessage(ChatMessageRole.ASSISTANT, "Hi"))));

        assertEquals("{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"Hello\"}]},{\"role\":\"model\",\"parts\":[{\"text\":\"Hi\"}]}],\"systemInstruction\":{\"parts\":[{\"text\":\"Be concise.\"}]},\"generationConfig\":{\"temperature\":0.7,\"topP\":0.9,\"topK\":40,\"maxOutputTokens\":64,\"stopSequences\":[\"STOP\"]}}", payload);
    }

    @Test
    void validatesGenerationParameters() {
        assertThrows(IllegalArgumentException.class, () -> GeminiChatModel.builder().apiKey("test-key").temperature(2.1).build());
        assertThrows(IllegalArgumentException.class, () -> GeminiChatModel.builder().apiKey("test-key").topP(-0.1).build());
        assertThrows(IllegalArgumentException.class, () -> GeminiChatModel.builder().apiKey("test-key").maxOutputTokens(0).build());
    }

    @Test
    void mapsToolResultsAsFunctionResponses() {
        var model = GeminiChatModel.builder().apiKey("test-key").build();
        var request = new ChatRequest(List.of(ChatMessage.toolResult("weather", "sunny")));
        assertEquals("{\"contents\":[{\"role\":\"user\",\"parts\":[{\"functionResponse\":{\"name\":\"weather\",\"id\":\"weather\",\"response\":{\"content\":\"sunny\"}}}]}]}", model.toRequestJson(request));
    }

    @Test
    void returnsThoughtSignaturesOnFunctionCallParts() throws Exception {
        var model = GeminiChatModel.builder().apiKey("test-key").build();
        var assistant = new ChatMessage(
                ChatMessageRole.ASSISTANT,
                "",
                List.of(),
                null,
                List.of(new ToolCall("call_123", "weather", Map.of("city", "Berlin"), Map.of("gemini.thoughtSignature", "sig_123"))),
                Map.of());

        String payload = model.toRequestJson(new ChatRequest(List.of(assistant)));

        assertEquals("{\"contents\":[{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"name\":\"weather\",\"id\":\"call_123\",\"args\":{\"city\":\"Berlin\"}},\"thoughtSignature\":\"sig_123\"}]}]}", payload);
    }

    @Test
    void mapsThinkingConfiguration() throws Exception {
        var model = GeminiChatModel.builder().apiKey("test-key")
                .thinkingConfig(Map.of("includeThoughts", true, "thinkingBudget", 1024))
                .build();

        String payload = model.toRequestJson(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, "Think."))));

        var config = new ObjectMapper().readTree(payload).path("generationConfig").path("thinkingConfig");
        assertEquals(true, config.path("includeThoughts").asBoolean());
        assertEquals(1024, config.path("thinkingBudget").asInt());
    }
}
