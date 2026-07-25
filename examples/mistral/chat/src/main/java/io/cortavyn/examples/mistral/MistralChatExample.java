package io.cortavyn.examples.mistral;

import io.cortavyn.examples.graph.ResearchConversationExample;
import io.cortavyn.provider.mistral.MistralChatModel;

/** Runs a two-turn research and review conversation through Mistral. */
public final class MistralChatExample {
    private MistralChatExample() {
    }

    public static void main(String[] args) {
        String prompt = System.getProperty("example.prompt", "What trade-offs should a team consider when introducing durable AI agents?");
        var builder = MistralChatModel.builder().apiKey(requiredEnvironment("MISTRAL_API_KEY"));
        String modelName = System.getenv("MISTRAL_MODEL");
        if (modelName != null && !modelName.isBlank()) builder.modelName(modelName);

        ResearchConversationExample.run("mistral", builder.build(), prompt);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
}
