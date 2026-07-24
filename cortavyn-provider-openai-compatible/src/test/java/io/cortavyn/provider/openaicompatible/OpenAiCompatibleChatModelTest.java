package io.cortavyn.provider.openaicompatible;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.model.api.ReasoningContent;
import java.util.List;
import org.junit.jupiter.api.Test;

@org.jspecify.annotations.NullMarked
class OpenAiCompatibleChatModelTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void serializesStandardChatCompletionsRequest() throws Exception {
        var model = OpenAiCompatibleChatModel.builder().baseUrl("https://example.test/v1")
                .apiKey("token").modelName("model").temperature(0.2).maxTokens(42).build();
        var json = JSON.readTree(model.toRequestJson(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, "Hello")))));
        assertEquals("model", json.path("model").asText());
        assertEquals(42, json.path("max_tokens").asInt());
        assertEquals("Hello", json.path("messages").path(0).path("content").asText());
    }

    @Test
    void mapsToolResultsWithTheirCallId() throws Exception {
        var model = OpenAiCompatibleChatModel.builder().baseUrl("https://example.test/v1").apiKey("token").modelName("model").build();
        var json = JSON.readTree(model.toRequestJson(new ChatRequest(List.of(ChatMessage.toolResult("call_1", "result")))));
        assertEquals("call_1", json.path("messages").path(0).path("tool_call_id").asText());
    }

    @Test
    void canReplayReasoningForProvidersThatRequireIt() throws Exception {
        var model = OpenAiCompatibleChatModel.builder().baseUrl("https://example.test/v1")
                .apiKey("token").modelName("model").preserveReasoningContent(true).build();
        var assistant = new ChatMessage(ChatMessageRole.ASSISTANT, List.of(new ReasoningContent("Need a tool.")));

        var json = JSON.readTree(model.toRequestJson(new ChatRequest(List.of(assistant))));

        assertEquals("Need a tool.", json.path("messages").path(0).path("reasoning_content").asText());
    }
}
