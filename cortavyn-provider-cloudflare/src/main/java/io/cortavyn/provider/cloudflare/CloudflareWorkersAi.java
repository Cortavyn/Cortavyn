package io.cortavyn.provider.cloudflare;
import io.cortavyn.provider.openaicompatible.OpenAiCompatibleChatModel;
/** Cloudflare Workers AI endpoint factory. */
public final class CloudflareWorkersAi { private CloudflareWorkersAi() { } public static OpenAiCompatibleChatModel chatModel(String accountId, String apiToken, String modelName) { return OpenAiCompatibleChatModel.builder().baseUrl("https://api.cloudflare.com/client/v4/accounts/" + accountId + "/ai/v1").apiKey(apiToken).modelName(modelName).build(); } }
