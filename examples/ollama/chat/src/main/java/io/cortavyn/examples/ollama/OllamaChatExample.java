package io.cortavyn.examples.ollama;

import io.cortavyn.examples.graph.ResearchConversationExample;
import io.cortavyn.provider.ollama.OllamaChatModel;
import java.net.URI;

/** Runs a two-turn research and review conversation through local Ollama. */
public final class OllamaChatExample {
    private OllamaChatExample() { }
    public static void main(String[] args) {
        String prompt = System.getProperty("example.prompt", "What trade-offs should a team consider when introducing durable AI agents?");
        var builder = OllamaChatModel.builder();
        String modelName = System.getenv("OLLAMA_MODEL");
        if (modelName != null && !modelName.isBlank()) builder.modelName(modelName);
        String baseUrl = System.getenv("OLLAMA_BASE_URL");
        if (baseUrl != null && !baseUrl.isBlank()) builder.baseUrl(URI.create(baseUrl));
        ResearchConversationExample.run("ollama", builder.build(), prompt);
    }
}
