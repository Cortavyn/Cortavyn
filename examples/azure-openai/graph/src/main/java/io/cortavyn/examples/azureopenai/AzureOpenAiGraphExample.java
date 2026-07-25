package io.cortavyn.examples.azureopenai;

import io.cortavyn.examples.graph.GraphWorkflowExample;
import io.cortavyn.provider.azureopenai.AzureOpenAiChatModel;
import java.net.URI;

/** Runs the two-step graph workflow through Azure OpenAI. */
public final class AzureOpenAiGraphExample {
    private AzureOpenAiGraphExample() { }
    public static void main(String[] args) {
        // Azure requires endpoint, deployment, API version, and credential as separate settings.
        var model = AzureOpenAiChatModel.builder()
                .endpoint(URI.create(required("AZURE_OPENAI_ENDPOINT")))
                .apiKey(required("AZURE_OPENAI_API_KEY"))
                .deploymentName(required("AZURE_OPENAI_DEPLOYMENT"))
                .apiVersion(required("AZURE_OPENAI_API_VERSION"))
                .build();
        // Once built, the adapter is used like every other portable ChatModel.
        GraphWorkflowExample.run("azure-openai", model, System.getProperty("example.prompt", "How should a team introduce durable AI agents responsibly?"));
    }
    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " must be set");
        return value;
    }
}
