package io.cortavyn.provider.deepseek;
import io.cortavyn.provider.openaicompatible.OpenAiCompatibleChatModel;
/** DeepSeek endpoint factory. */
public final class DeepSeek { private DeepSeek() { } public static OpenAiCompatibleChatModel chatModel(String apiKey, String modelName) { return OpenAiCompatibleChatModel.builder().baseUrl("https://api.deepseek.com/v1").apiKey(apiKey).modelName(modelName).build(); } }
