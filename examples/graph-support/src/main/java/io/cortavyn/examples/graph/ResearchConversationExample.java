package io.cortavyn.examples.graph;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatRequest;
import java.util.List;

/** Provider-neutral two-turn conversation that demonstrates refinement instead of one-shot chat. */
public final class ResearchConversationExample {
    private ResearchConversationExample() { }

    /** Runs a draft-and-review conversation and prints both useful intermediate artefacts. */
    public static void run(String provider, ChatModel model, String question) {
        ChatMessage system = new ChatMessage(ChatMessageRole.SYSTEM,
                "You are a careful research assistant. Distinguish established facts, assumptions, and uncertainty.");
        ChatMessage user = new ChatMessage(ChatMessageRole.USER, question);
        ChatMessage draft = model.complete(new ChatRequest(List.of(system, user))).toCompletableFuture().join().message();

        ChatMessage reviewPrompt = new ChatMessage(ChatMessageRole.USER,
                "Critically improve the draft. Remove unsupported claims, mention uncertainty, and return a concise final answer.");
        ChatMessage finalAnswer = model.complete(new ChatRequest(List.of(system, user, draft, reviewPrompt)))
                .toCompletableFuture().join().message();

        System.out.println("provider=" + provider);
        System.out.println("=== Draft ===\n" + draft.content());
        System.out.println("=== Reviewed answer ===\n" + finalAnswer.content());
    }
}
