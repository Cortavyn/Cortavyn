package io.cortavyn.examples.anthropic;

import io.cortavyn.examples.graph.ResearchConversationExample;
import io.cortavyn.provider.anthropic.AnthropicChatModel;

/** Runs a two-turn research and review conversation through Anthropic. */
public final class AnthropicChatExample {
    private AnthropicChatExample() { }
    public static void main(String[] args) {
        String prompt = System.getProperty("example.prompt", "What trade-offs should a team consider when introducing durable AI agents?");
        var builder = AnthropicChatModel.builder().apiKey(requiredEnvironment("ANTHROPIC_API_KEY"));
        String modelName = System.getenv("ANTHROPIC_MODEL");
        if (modelName != null && !modelName.isBlank()) builder.modelName(modelName);
        ResearchConversationExample.run("anthropic", builder.build(), prompt);
    }
    private static String requiredEnvironment(String name) { String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set"); return value; }
}
