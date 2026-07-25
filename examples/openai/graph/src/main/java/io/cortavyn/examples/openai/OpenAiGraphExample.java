package io.cortavyn.examples.openai;

import io.cortavyn.examples.graph.GraphWorkflowExample;
import io.cortavyn.provider.openai.OpenAiChatModel;

/** Runs the two-step graph workflow through OpenAI. */
public final class OpenAiGraphExample {
    private OpenAiGraphExample() { }

    public static void main(String[] args) {
        // Select a safe default model while allowing an application-specific override.
        var model = OpenAiChatModel.builder()
                .apiKey(required("OPENAI_API_KEY"))
                .modelName(System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini"))
                .build();

        // Run the same durable graph shape used by every provider example.
        GraphWorkflowExample.run(
                "openai",
                model,
                System.getProperty("example.prompt", "Explain durable agents in one sentence."));
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " must be set");
        }
        return value;
    }
}
