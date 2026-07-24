package io.cortavyn.model.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChatRequestTest {
    @Test
    void preservesMessageOrder() {
        var message = new ChatMessage(ChatMessageRole.USER, "Hello");
        assertEquals(List.of(message), new ChatRequest(List.of(message)).messages());
    }
}
