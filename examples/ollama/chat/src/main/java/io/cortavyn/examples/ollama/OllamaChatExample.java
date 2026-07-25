package io.cortavyn.examples.ollama;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.provider.ollama.OllamaChatModel;
import java.net.URI;
import java.util.List;

/** Runs one local Ollama chat request when explicitly invoked through Maven. */
public final class OllamaChatExample {
    private OllamaChatExample() { }
    public static void main(String[] args) {
        String prompt = System.getProperty("example.prompt", "Explain durable agents in one sentence.");
        var builder = OllamaChatModel.builder();
        String modelName = System.getenv("OLLAMA_MODEL");
        if (modelName != null && !modelName.isBlank()) builder.modelName(modelName);
        String baseUrl = System.getenv("OLLAMA_BASE_URL");
        if (baseUrl != null && !baseUrl.isBlank()) builder.baseUrl(URI.create(baseUrl));
        var response = builder.build().complete(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, prompt)))).toCompletableFuture().join();
        System.out.println(response.message().content());
    }
}
