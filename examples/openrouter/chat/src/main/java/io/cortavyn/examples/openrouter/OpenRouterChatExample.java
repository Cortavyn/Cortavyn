package io.cortavyn.examples.openrouter;

import io.cortavyn.examples.graph.ResearchConversationExample;
import io.cortavyn.provider.openrouter.OpenRouterChatModel;

/** Runs a two-turn research and review conversation through OpenRouter. */
public final class OpenRouterChatExample {
    private OpenRouterChatExample() { }

    public static void main(String[] args) {
        String prompt = System.getProperty("example.prompt", "What trade-offs should a team consider when introducing durable AI agents?");
        var builder = OpenRouterChatModel.builder().apiKey(requiredEnvironment("OPENROUTER_API_KEY"));
        String modelName = System.getenv("OPENROUTER_MODEL");
        if (modelName != null && !modelName.isBlank()) builder.modelName(modelName);
        String siteUrl = System.getenv("OPENROUTER_SITE_URL");
        if (siteUrl != null && !siteUrl.isBlank()) builder.siteUrl(siteUrl);
        String appTitle = System.getenv("OPENROUTER_APP_TITLE");
        if (appTitle != null && !appTitle.isBlank()) builder.appTitle(appTitle);

        ResearchConversationExample.run("openrouter", builder.build(), prompt);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
}
