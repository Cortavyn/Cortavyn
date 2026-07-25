package io.cortavyn.examples.gemini;

import io.cortavyn.examples.graph.GraphWorkflowExample;
import io.cortavyn.provider.gemini.GeminiChatModel;

/** Runs the two-step graph workflow through Gemini. */
public final class GeminiGraphExample {
    private GeminiGraphExample() { }
    public static void main(String[] args) {
        // Gemini accepts the standard Google key first, then the Gemini-specific fallback.
        var builder = GeminiChatModel.builder().apiKey(requiredApiKey());
        String modelName = System.getenv("GEMINI_MODEL");
        if (modelName != null && !modelName.isBlank()) builder.modelName(modelName);
        // Delegate graph construction and checkpoint inspection to the shared workflow.
        GraphWorkflowExample.run("gemini", builder.build(), System.getProperty("example.prompt", "How should a team introduce durable AI agents responsibly?"));
    }
    private static String requiredApiKey() {
        String value = System.getenv("GOOGLE_API_KEY");
        if (value == null || value.isBlank()) value = System.getenv("GEMINI_API_KEY");
        if (value == null || value.isBlank()) throw new IllegalStateException("GOOGLE_API_KEY or GEMINI_API_KEY must be set");
        return value;
    }
}
