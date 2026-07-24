package io.cortavyn.provider.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import java.util.List;
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
    void rejectsToolMessagesUntilThePortableApiCanExpressToolCallIds() {
        var model = OpenAiChatModel.builder().apiKey("test-key").modelName("gpt-test").build();
        var request = new ChatRequest(List.of(new ChatMessage(ChatMessageRole.TOOL, "result")));

        assertThrows(IllegalArgumentException.class, () -> model.toRequestJson(request));
    }

    @Test
    void requiresCredentialsAndModelSelection() {
        assertThrows(IllegalArgumentException.class, () -> OpenAiChatModel.builder().modelName("gpt-test").build());
        assertThrows(IllegalArgumentException.class, () -> OpenAiChatModel.builder().apiKey("test-key").build());
    }
}
