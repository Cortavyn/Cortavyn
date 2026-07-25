package io.cortavyn.examples.ollama;

import io.cortavyn.examples.graph.GraphWorkflowExample;
import io.cortavyn.provider.ollama.OllamaChatModel;
import java.net.URI;

/** Runs the two-step graph workflow through Ollama. */
public final class OllamaGraphExample {
    private OllamaGraphExample() { }
    public static void main(String[] args) {
        // No credentials are needed for local Ollama; model and endpoint remain optional overrides.
        var builder = OllamaChatModel.builder();
        String modelName = System.getenv("OLLAMA_MODEL");
        if (modelName != null && !modelName.isBlank()) builder.modelName(modelName);
        String baseUrl = System.getenv("OLLAMA_BASE_URL");
        if (baseUrl != null && !baseUrl.isBlank()) builder.baseUrl(URI.create(baseUrl));
        // The same graph can therefore be exercised entirely locally.
        GraphWorkflowExample.run("ollama", builder.build(), System.getProperty("example.prompt", "Explain durable agents in one sentence."));
    }
}
