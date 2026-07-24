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
}
