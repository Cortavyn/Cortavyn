package io.cortavyn.provider.mistral;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.model.api.ImageContent;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MistralChatModelTest {
    @Test
    void mapsMistralDefaultsAndProviderSpecificParameters() {
        var model = MistralChatModel.builder()
                .apiKey("test-key")
                .maxTokens(64)
                .randomSeed(7)
                .safePrompt(true)
                .reasoningEffort("high")
                .additionalParameters(Map.of("presence_penalty", 0.1))
                .build();

        String payload = model.toRequestJson(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, "Hello"))));

        assertEquals("{\"model\":\"mistral-small\",\"temperature\":0.7,\"top_p\":1.0,\"max_tokens\":64,\"random_seed\":7,\"safe_prompt\":true,\"reasoning_effort\":\"high\",\"presence_penalty\":0.1,\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}", payload);
    }

    @Test
    void validatesMistralParameterRanges() {
        assertThrows(IllegalArgumentException.class, () -> MistralChatModel.builder().apiKey("test-key").temperature(1.1).build());
        assertThrows(IllegalArgumentException.class, () -> MistralChatModel.builder().apiKey("test-key").topP(-0.1).build());
    }

    @Test
    void mapsToolResultsWithToolCallIds() {
        var model = MistralChatModel.builder().apiKey("test-key").build();
        var request = new ChatRequest(List.of(ChatMessage.toolResult("call_1", "result")));
        assertEquals(true, model.toRequestJson(request).contains("\"tool_call_id\":\"call_1\""));
    }

    @Test
    void mapsImageContentToMistralsVisionShape() {
        var model = MistralChatModel.builder().apiKey("test-key").build();
        String payload = model.toRequestJson(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, List.of(
                new ImageContent(URI.create("https://example.test/image.png"), "image/png"))))));
        assertEquals(true, payload.contains("\"type\":\"image_url\",\"image_url\":\"https://example.test/image.png\""));
    }
}
