package io.cortavyn.examples.openrouter;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.provider.openrouter.OpenRouterChatModel;
import java.util.List;

/** Runs one real OpenRouter chat completion when explicitly invoked through Maven. */
public final class OpenRouterChatExample {
    private OpenRouterChatExample() { }

    public static void main(String[] args) {
        String prompt = System.getProperty("example.prompt", "Explain durable agents in one sentence.");
        var builder = OpenRouterChatModel.builder().apiKey(requiredEnvironment("OPENROUTER_API_KEY"));
        String modelName = System.getenv("OPENROUTER_MODEL");
        if (modelName != null && !modelName.isBlank()) builder.modelName(modelName);
        String siteUrl = System.getenv("OPENROUTER_SITE_URL");
        if (siteUrl != null && !siteUrl.isBlank()) builder.siteUrl(siteUrl);
        String appTitle = System.getenv("OPENROUTER_APP_TITLE");
        if (appTitle != null && !appTitle.isBlank()) builder.appTitle(appTitle);

        var response = builder.build()
                .complete(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, prompt))))
                .toCompletableFuture()
                .join();
        System.out.println(response.message().content());
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
}
