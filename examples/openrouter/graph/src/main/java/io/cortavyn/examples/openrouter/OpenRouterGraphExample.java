package io.cortavyn.examples.openrouter;

import io.cortavyn.examples.graph.GraphWorkflowExample;
import io.cortavyn.provider.openrouter.OpenRouterChatModel;

/** Runs the two-step graph workflow through OpenRouter. */
public final class OpenRouterGraphExample {
    private OpenRouterGraphExample() { }
    public static void main(String[] args) {
        // OpenRouter model selection stays configurable through its environment variable.
        var builder = OpenRouterChatModel.builder().apiKey(required("OPENROUTER_API_KEY"));
        String modelName = System.getenv("OPENROUTER_MODEL");
        if (modelName != null && !modelName.isBlank()) builder.modelName(modelName);
        // Run draft and finalization nodes against the configured gateway model.
        GraphWorkflowExample.run("openrouter", builder.build(), System.getProperty("example.prompt", "How should a team introduce durable AI agents responsibly?"));
    }
    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " must be set");
        return value;
    }
}
