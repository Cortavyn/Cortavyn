package io.cortavyn.examples.gemini;

import io.cortavyn.examples.graph.ResearchConversationExample;
import io.cortavyn.provider.gemini.GeminiChatModel;

/** Runs a two-turn research and review conversation through Gemini. */
public final class GeminiChatExample {
    private GeminiChatExample() { }

    public static void main(String[] args) {
        String prompt = System.getProperty("example.prompt", "What trade-offs should a team consider when introducing durable AI agents?");
        var builder = GeminiChatModel.builder().apiKey(requiredApiKey());
        String modelName = System.getenv("GEMINI_MODEL");
        if (modelName != null && !modelName.isBlank()) builder.modelName(modelName);

        ResearchConversationExample.run("gemini", builder.build(), prompt);
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
