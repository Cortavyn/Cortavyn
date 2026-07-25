package io.cortavyn.examples.anthropic;

import io.cortavyn.examples.graph.GraphWorkflowExample;
import io.cortavyn.provider.anthropic.AnthropicChatModel;

/** Runs the two-step graph workflow through Anthropic. */
public final class AnthropicGraphExample {
    private AnthropicGraphExample() { }
    public static void main(String[] args) {
        // Build the provider adapter only after the caller supplied an API key.
        var builder = AnthropicChatModel.builder().apiKey(required("ANTHROPIC_API_KEY"));
        String modelName = System.getenv("ANTHROPIC_MODEL");
        if (modelName != null && !modelName.isBlank()) builder.modelName(modelName);
        // Execute the shared stateful workflow with this provider implementation.
        GraphWorkflowExample.run("anthropic", builder.build(), System.getProperty("example.prompt", "Explain durable agents in one sentence."));
    }
    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " must be set");
        return value;
    }
}
