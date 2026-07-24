package io.cortavyn.provider.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

class OpenAiResponsesChatModelTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void continuesFromTheLatestResponsesApiMessageAutomatically() throws Exception {
        var model = OpenAiResponsesChatModel.builder().apiKey("test-key").modelName("gpt-test").build();
        @Nullable String noToolCallId = null;
        var previousAnswer = new ChatMessage(
                ChatMessageRole.ASSISTANT,
                "The capital is Paris.",
                List.of(),
                noToolCallId,
                List.of(),
                Map.of("openai.response_id", "resp_123"));
        var request = new ChatRequest(List.of(
                new ChatMessage(ChatMessageRole.USER, "What is the capital of France?"),
                previousAnswer,
                new ChatMessage(ChatMessageRole.USER, "And of Germany?")));

        var payload = JSON.readTree(model.toRequestJson(request));

        assertEquals("resp_123", payload.path("previous_response_id").asText());
        assertEquals(1, payload.path("input").size());
        assertEquals("And of Germany?", payload.path("input").path(0).path("content").asText());
    }

    @Test
    void allowsAnExplicitPreviousResponseId() throws Exception {
        var model = OpenAiResponsesChatModel.builder().apiKey("test-key").modelName("gpt-test").build();
        var request = new ChatRequest(
                List.of(new ChatMessage(ChatMessageRole.USER, "Continue.")),
                List.of(),
                io.cortavyn.model.api.ChatGenerationParameters.defaults(),
                Map.of("openai.previous_response_id", "resp_explicit"));

        var payload = JSON.readTree(model.toRequestJson(request));

        assertEquals("resp_explicit", payload.path("previous_response_id").asText());
        assertEquals("Continue.", payload.path("input").path(0).path("content").asText());
    }

    @Test
    void mapsToolsToTheResponsesApiWireFormat() throws Exception {
        var model = OpenAiResponsesChatModel.builder().apiKey("test-key").modelName("gpt-test").build();
        var request = new ChatRequest(
                List.of(new ChatMessage(ChatMessageRole.USER, "What is the weather?")),
                List.of(new io.cortavyn.model.api.ToolDefinition(
                        "weather",
                        "Gets the weather for a city.",
                        Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string"))))),
                io.cortavyn.model.api.ChatGenerationParameters.defaults(),
                Map.of());

        var payload = JSON.readTree(model.toRequestJson(request));

        assertEquals("function", payload.path("tools").path(0).path("type").asText());
        assertEquals("weather", payload.path("tools").path(0).path("name").asText());
        assertEquals("string", payload.path("tools").path(0).path("parameters").path("properties").path("city").path("type").asText());
    }

    @Test
    void mapsToolResultsToFunctionCallOutputs() throws Exception {
        var model = OpenAiResponsesChatModel.builder().apiKey("test-key").modelName("gpt-test").build();
        var payload = JSON.readTree(model.toRequestJson(new ChatRequest(List.of(ChatMessage.toolResult("call_123", "sunny")))));

        assertEquals("function_call_output", payload.path("input").path(0).path("type").asText());
        assertEquals("call_123", payload.path("input").path(0).path("call_id").asText());
        assertEquals("sunny", payload.path("input").path(0).path("output").asText());
    }
}
