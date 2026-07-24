package io.cortavyn.examples.openai;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.provider.openai.OpenAiChatModel;
import java.util.List;

/** Runs one real OpenAI chat completion when explicitly invoked through Maven. */
public final class OpenAiChatExample {
    private OpenAiChatExample() {
    }

    public static void main(String[] args) {
        String prompt = System.getProperty("example.prompt", "Explain durable agents in one sentence.");
        String modelName = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini");
        var model = OpenAiChatModel.builder()
                .apiKey(requiredEnvironment("OPENAI_API_KEY"))
                .modelName(modelName)
                .build();

        var response = model.complete(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, prompt))))
                .toCompletableFuture()
                .join();
        System.out.println(response.message().content());
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
}
