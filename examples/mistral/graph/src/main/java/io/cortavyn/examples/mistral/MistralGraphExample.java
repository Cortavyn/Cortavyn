package io.cortavyn.examples.mistral;

import io.cortavyn.examples.graph.GraphWorkflowExample;
import io.cortavyn.provider.mistral.MistralChatModel;

/** Runs the two-step graph workflow through Mistral. */
public final class MistralGraphExample {
    private MistralGraphExample() { }

    public static void main(String[] args) {
        // Mistral credentials are read only when this executable example is run.
        var builder = MistralChatModel.builder().apiKey(required("MISTRAL_API_KEY"));
        // The environment override keeps the example usable with any available Mistral model.
        String modelName = System.getenv("MISTRAL_MODEL");
        if (modelName != null && !modelName.isBlank()) {
            builder.modelName(modelName);
        }

        // The reusable workflow performs draft -> conditional route -> final answer.
        GraphWorkflowExample.run(
                "mistral",
                builder.build(),
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
