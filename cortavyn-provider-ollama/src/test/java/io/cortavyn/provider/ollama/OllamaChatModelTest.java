package io.cortavyn.provider.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OllamaChatModelTest {
    @Test void mapsTheNativeChatApiAndDisablesStreamingForThePortableCall() {
        var model = OllamaChatModel.builder().modelName("qwen3").options(Map.of("temperature", 0.7, "num_predict", 64)).think(true).keepAlive("5m").build();
        String payload = model.toRequestJson(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.SYSTEM, "Be concise."), new ChatMessage(ChatMessageRole.USER, "Hello"))));
        assertEquals("{\"model\":\"qwen3\",\"stream\":false,\"options\":{\"num_predict\":64,\"temperature\":0.7},\"think\":true,\"keep_alive\":\"5m\",\"messages\":[{\"role\":\"system\",\"content\":\"Be concise.\"},{\"role\":\"user\",\"content\":\"Hello\"}]}", payload);
    }

    @Test void rejectsToolMessagesUntilPortableToolCallsExist() {
        var model = OllamaChatModel.builder().build();
        assertThrows(IllegalArgumentException.class, () -> model.toRequestJson(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.TOOL, "result")))));
    }
}
