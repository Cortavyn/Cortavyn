package io.cortavyn.examples.azureopenai;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.provider.azureopenai.AzureOpenAiChatModel;
import java.net.URI;
import java.util.List;

/** Runs one Azure OpenAI chat completion when explicitly invoked through Maven. */
public final class AzureOpenAiChatExample {
    private AzureOpenAiChatExample() { }
    public static void main(String[] args) {
        String prompt = System.getProperty("example.prompt", "Explain durable agents in one sentence.");
        var model = AzureOpenAiChatModel.builder()
                .endpoint(URI.create(requiredEnvironment("AZURE_OPENAI_ENDPOINT")))
                .apiKey(requiredEnvironment("AZURE_OPENAI_API_KEY"))
                .deploymentName(requiredEnvironment("AZURE_OPENAI_DEPLOYMENT"))
                .apiVersion(requiredEnvironment("AZURE_OPENAI_API_VERSION"))
                .build();
        var response = model.complete(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, prompt))))
                .toCompletableFuture().join();
        System.out.println(response.message().content());
    }
    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
}
