package io.cortavyn.examples.anthropic;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.provider.anthropic.AnthropicChatModel;
import java.util.List;

/** Runs one real Anthropic Messages API request when explicitly invoked through Maven. */
public final class AnthropicChatExample {
    private AnthropicChatExample() { }
    public static void main(String[] args) {
        String prompt = System.getProperty("example.prompt", "Explain durable agents in one sentence.");
        var builder = AnthropicChatModel.builder().apiKey(requiredEnvironment("ANTHROPIC_API_KEY"));
        String modelName = System.getenv("ANTHROPIC_MODEL");
        if (modelName != null && !modelName.isBlank()) builder.modelName(modelName);
        var response = builder.build().complete(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, prompt)))).toCompletableFuture().join();
        System.out.println(response.message().content());
    }
    private static String requiredEnvironment(String name) { String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set"); return value; }
}
