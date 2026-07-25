package io.cortavyn.examples.azureopenai;

import io.cortavyn.examples.graph.ResearchConversationExample;
import io.cortavyn.provider.azureopenai.AzureOpenAiChatModel;
import java.net.URI;

/** Runs a two-turn research and review conversation through Azure OpenAI. */
public final class AzureOpenAiChatExample {
    private AzureOpenAiChatExample() { }
    public static void main(String[] args) {
        String prompt = System.getProperty("example.prompt", "What trade-offs should a team consider when introducing durable AI agents?");
        var model = AzureOpenAiChatModel.builder()
                .endpoint(URI.create(requiredEnvironment("AZURE_OPENAI_ENDPOINT")))
                .apiKey(requiredEnvironment("AZURE_OPENAI_API_KEY"))
                .deploymentName(requiredEnvironment("AZURE_OPENAI_DEPLOYMENT"))
                .apiVersion(requiredEnvironment("AZURE_OPENAI_API_VERSION"))
                .build();
        ResearchConversationExample.run("azure-openai", model, prompt);
    }
    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
}
