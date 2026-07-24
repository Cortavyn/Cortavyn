package io.cortavyn.provider.qwen;
import io.cortavyn.provider.openaicompatible.OpenAiCompatibleChatModel;
/** Qwen DashScope compatible-mode endpoint factory. */
public final class Qwen { private Qwen() { } public static OpenAiCompatibleChatModel chatModel(String apiKey, String modelName) { return OpenAiCompatibleChatModel.builder().baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1").apiKey(apiKey).modelName(modelName).build(); } }
