package io.cortavyn.provider.azureopenai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

@org.jspecify.annotations.NullMarked
class AzureOpenAiChatModelTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void serializesDeploymentIndependentChatRequest() throws Exception {
        AzureOpenAiChatModel model = AzureOpenAiChatModel.builder()
                .endpoint(URI.create("https://example.openai.azure.com"))
                .apiKey("secret")
                .deploymentName("gpt-4.1")
                .apiVersion("2025-04-01-preview")
                .maxTokens(120)
                .build();

        var json = JSON.readTree(model.toRequestJson(new ChatRequest(List.of(
                new ChatMessage(ChatMessageRole.SYSTEM, "Be concise."),
                new ChatMessage(ChatMessageRole.USER, "Hello")))));

        assertEquals(120, json.path("max_completion_tokens").asInt());
        assertEquals("system", json.path("messages").path(0).path("role").asText());
        assertEquals("Hello", json.path("messages").path(1).path("content").asText());
    }

    @Test
    void requiresAzureDeploymentConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> AzureOpenAiChatModel.builder()
                .endpoint(URI.create("https://example.openai.azure.com"))
                .apiKey("secret")
                .apiVersion("2025-04-01-preview")
                .build());
    }
}
