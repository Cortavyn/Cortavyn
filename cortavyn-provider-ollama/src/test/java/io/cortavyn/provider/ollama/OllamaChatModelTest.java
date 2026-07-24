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
        assertEquals(true, payload.contains("\"model\":\"qwen3\""));
        assertEquals(true, payload.contains("\"stream\":false"));
        assertEquals(true, payload.contains("\"role\":\"user\",\"content\":\"Hello\""));
    }

    @Test void mapsToolMessages() {
        var model = OllamaChatModel.builder().build();
        assertEquals(true, model.toRequestJson(new ChatRequest(List.of(ChatMessage.toolResult("call_1", "result")))).contains("\"role\":\"tool\""));
    }
}
