package io.cortavyn.provider.vercel;
import io.cortavyn.provider.openaicompatible.OpenAiCompatibleChatModel;
/** Vercel AI Gateway endpoint factory. */
public final class VercelAiGateway { private VercelAiGateway() { } public static OpenAiCompatibleChatModel chatModel(String apiKey, String modelName) { return chatModel(apiKey, modelName, false, false); } public static OpenAiCompatibleChatModel chatModel(String apiKey, String modelName, boolean supportsImages, boolean supportsAudio) { return OpenAiCompatibleChatModel.builder().baseUrl("https://ai-gateway.vercel.sh/v1").apiKey(apiKey).modelName(modelName).supportsImages(supportsImages).supportsAudio(supportsAudio).build(); } }
