package io.cortavyn.provider.xai;
import io.cortavyn.provider.openaicompatible.OpenAiCompatibleChatModel;
/** xAI endpoint factory. */
public final class Xai { private Xai() { } public static OpenAiCompatibleChatModel chatModel(String apiKey, String modelName) { return OpenAiCompatibleChatModel.builder().baseUrl("https://api.x.ai/v1").apiKey(apiKey).modelName(modelName).build(); } }
