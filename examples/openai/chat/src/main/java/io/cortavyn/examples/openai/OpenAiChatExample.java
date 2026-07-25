package io.cortavyn.examples.openai;

import io.cortavyn.examples.graph.ResearchConversationExample;
import io.cortavyn.provider.openai.OpenAiChatModel;

/** Runs a two-turn research and review conversation through OpenAI. */
public final class OpenAiChatExample {
    private OpenAiChatExample() {
    }

    public static void main(String[] args) {
        String prompt = System.getProperty("example.prompt", "What trade-offs should a team consider when introducing durable AI agents?");
        String modelName = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini");
        var model = OpenAiChatModel.builder()
                .apiKey(requiredEnvironment("OPENAI_API_KEY"))
                .modelName(modelName)
                .build();

        ResearchConversationExample.run("openai", model, prompt);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
}
