package io.cortavyn.chat;

import io.cortavyn.model.api.ChatMessage;
import java.util.concurrent.CompletionStage;

/** Appends a user turn and returns the updated conversation asynchronously. */
@FunctionalInterface
public interface ChatSession {
    CompletionStage<Conversation> reply(Conversation conversation, ChatMessage userMessage);
}
