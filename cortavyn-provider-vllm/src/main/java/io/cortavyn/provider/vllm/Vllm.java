package io.cortavyn.provider.vllm;
import io.cortavyn.provider.openaicompatible.OpenAiCompatibleChatModel;
/** vLLM endpoint factory. */
public final class Vllm { private Vllm() { } public static OpenAiCompatibleChatModel chatModel(String modelName) { return chatModel("http://localhost:8000/v1", "unused", modelName); } public static OpenAiCompatibleChatModel chatModel(String baseUrl, String apiKey, String modelName) { return OpenAiCompatibleChatModel.builder().baseUrl(baseUrl).apiKey(apiKey).modelName(modelName).build(); } }
