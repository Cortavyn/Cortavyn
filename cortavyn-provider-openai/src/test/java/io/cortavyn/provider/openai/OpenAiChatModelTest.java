package io.cortavyn.provider.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.model.api.AudioContent;
import io.cortavyn.model.api.DocumentContent;
import io.cortavyn.model.api.ImageContent;
import io.cortavyn.model.api.StructuredOutputSchema;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiChatModelTest {
    @Test
    void mapsPortableMessagesToTheChatCompletionsWireFormat() {
        var model = OpenAiChatModel.builder().apiKey("test-key").modelName("gpt-test").build();

        String payload = model.toRequestJson(new ChatRequest(List.of(
                new ChatMessage(ChatMessageRole.SYSTEM, "Be concise."),
                new ChatMessage(ChatMessageRole.USER, "Hello"))));

        assertEquals("{\"model\":\"gpt-test\",\"messages\":[{\"role\":\"system\",\"content\":\"Be concise.\"},{\"role\":\"user\",\"content\":\"Hello\"}]}", payload);
    }

    @Test
    void mapsToolResultsWithTheirToolCallId() {
        var model = OpenAiChatModel.builder().apiKey("test-key").modelName("gpt-test").build();
        var request = new ChatRequest(List.of(ChatMessage.toolResult("call_123", "result")));

        assertEquals("{\"model\":\"gpt-test\",\"messages\":[{\"role\":\"tool\",\"content\":\"result\",\"tool_call_id\":\"call_123\"}]}", model.toRequestJson(request));
    }

    @Test
    void mapsLangChainCompatibleChatCompletionParameters() {
        var model = OpenAiChatModel.builder()
                .apiKey("test-key")
                .modelName("gpt-test")
                .temperature(0.7)
                .maxTokens(64)
                .topP(0.9)
                .frequencyPenalty(0.1)
                .presencePenalty(-0.1)
                .seed(7)
                .stopSequences(List.of("STOP"))
                .additionalParameters(Map.of("logprobs", true))
                .build();

        String payload = model.toRequestJson(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, "Hello"))));

        assertEquals("{\"model\":\"gpt-test\",\"temperature\":0.7,\"max_tokens\":64,\"top_p\":0.9,\"frequency_penalty\":0.1,\"presence_penalty\":-0.1,\"seed\":7,\"stop\":[\"STOP\"],\"logprobs\":true,\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}", payload);
    }

    @Test
    void requiresCredentialsAndModelSelection() {
        assertThrows(IllegalArgumentException.class, () -> OpenAiChatModel.builder().modelName("gpt-test").build());
        assertThrows(IllegalArgumentException.class, () -> OpenAiChatModel.builder().apiKey("test-key").build());
    }

    @Test
    void validatesSamplingParameterRanges() {
        assertThrows(IllegalArgumentException.class, () -> OpenAiChatModel.builder().apiKey("test-key").modelName("gpt-test").temperature(2.1).build());
        assertThrows(IllegalArgumentException.class, () -> OpenAiChatModel.builder().apiKey("test-key").modelName("gpt-test").presencePenalty(-2.1).build());
    }

    @Test
    void mapsStructuredOutputToOpenAiJsonSchemaFormat() {
        var model = OpenAiChatModel.builder().apiKey("test-key").modelName("gpt-test").build();
        String payload = model.toRequestJson(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, "Hello"))),
                new StructuredOutputSchema("answer", Map.of("type", "object"), true));
        assertEquals("{\"model\":\"gpt-test\",\"response_format\":{\"type\":\"json_schema\",\"json_schema\":{\"name\":\"answer\",\"strict\":true,\"schema\":{\"type\":\"object\"}}},\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}", payload);
    }

    @Test
    void mapsImagesAndAudioToChatCompletionsContent() {
        var model = OpenAiChatModel.builder().apiKey("test-key").modelName("gpt-test").build();
        String payload = model.toRequestJson(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, List.of(
                new ImageContent(URI.create("https://example.test/image.png"), "image/png"),
                new AudioContent(URI.create("data:audio/wav;base64,YXVkaW8="), "audio/wav"))))));
        assertEquals(true, payload.contains("\"type\":\"image_url\""));
        assertEquals(true, payload.contains("\"type\":\"input_audio\",\"input_audio\":{\"data\":\"YXVkaW8=\",\"format\":\"wav\"}"));
    }

    @Test
    void rejectsDocumentsWithAClearCapabilityError() {
        var model = OpenAiChatModel.builder().apiKey("test-key").modelName("gpt-test").build();
        var error = assertThrows(IllegalArgumentException.class, () -> model.toRequestJson(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, List.of(
                new DocumentContent(URI.create("data:application/pdf;base64,cGRm"), "application/pdf", "report.pdf")))))));
        assertEquals("OpenAI Chat Completions does not support DocumentContent", error.getMessage());
    }
}
