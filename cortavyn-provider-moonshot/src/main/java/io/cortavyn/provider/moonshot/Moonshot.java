package io.cortavyn.provider.moonshot;
import io.cortavyn.provider.openaicompatible.OpenAiCompatibleChatModel;
/** Moonshot endpoint factory. */
public final class Moonshot { private Moonshot() { } public static OpenAiCompatibleChatModel chatModel(String apiKey, String modelName) { return OpenAiCompatibleChatModel.builder().baseUrl("https://api.moonshot.ai/v1").apiKey(apiKey).modelName(modelName).build(); } }
