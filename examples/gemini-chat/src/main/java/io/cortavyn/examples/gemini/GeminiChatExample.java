package io.cortavyn.examples.gemini;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.provider.gemini.GeminiChatModel;
import java.util.List;

/** Runs one real Gemini Developer API request when explicitly invoked through Maven. */
public final class GeminiChatExample {
    private GeminiChatExample() { }

    public static void main(String[] args) {
        String prompt = System.getProperty("example.prompt", "Explain durable agents in one sentence.");
        var builder = GeminiChatModel.builder().apiKey(requiredApiKey());
        String modelName = System.getenv("GEMINI_MODEL");
        if (modelName != null && !modelName.isBlank()) builder.modelName(modelName);

        var response = builder.build()
                .complete(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, prompt))))
                .toCompletableFuture()
                .join();
        System.out.println(response.message().content());
    }

    private static String requiredApiKey() {
        String value = System.getenv("GOOGLE_API_KEY");
        if (value == null || value.isBlank()) value = System.getenv("GEMINI_API_KEY");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("GOOGLE_API_KEY or GEMINI_API_KEY must be set");
        }
        return value;
    }
}
