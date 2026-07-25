package io.cortavyn.examples.bedrock;

import io.cortavyn.examples.graph.GraphWorkflowExample;
import io.cortavyn.provider.bedrock.BedrockChatModel;

/** Runs the two-step graph workflow through AWS Bedrock. */
public final class BedrockGraphExample {
    private BedrockGraphExample() { }
    public static void main(String[] args) {
        // The model owns AWS resources, so it is closed after the graph completes.
        try (var model = BedrockChatModel.builder().modelId(required("AWS_BEDROCK_MODEL")).build()) {
            // Credentials and region come from the AWS default provider chains.
            GraphWorkflowExample.run("aws-bedrock", model, System.getProperty("example.prompt", "Explain durable agents in one sentence."));
        }
    }
    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " must be set");
        return value;
    }
}
