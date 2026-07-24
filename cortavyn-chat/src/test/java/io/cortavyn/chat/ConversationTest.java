package io.cortavyn.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationTest {
    @Test
    void retainsConversationMessages() {
        var conversation = new Conversation("conversation-1", List.of(new ChatMessage(ChatMessageRole.USER, "Hi")));
        assertEquals(1, conversation.messages().size());
    }
}
