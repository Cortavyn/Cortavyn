package io.cortavyn.provider.groq;
import io.cortavyn.provider.openaicompatible.OpenAiCompatibleChatModel;
/** Groq endpoint factory. */
public final class Groq { private Groq() { } public static OpenAiCompatibleChatModel chatModel(String apiKey, String modelName) { return OpenAiCompatibleChatModel.builder().baseUrl("https://api.groq.com/openai/v1").apiKey(apiKey).modelName(modelName).build(); } }
