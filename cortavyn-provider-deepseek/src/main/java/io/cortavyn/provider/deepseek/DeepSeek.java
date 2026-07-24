package io.cortavyn.provider.deepseek;
import io.cortavyn.provider.openaicompatible.OpenAiCompatibleChatModel;
/** DeepSeek endpoint factory. */
public final class DeepSeek {
    private DeepSeek() { }

    public static OpenAiCompatibleChatModel chatModel(String apiKey, String modelName) {
        return OpenAiCompatibleChatModel.builder()
                .baseUrl("https://api.deepseek.com/v1")
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }

    /** Creates a DeepSeek model that enables thinking and preserves reasoning across tool-call turns. */
    public static OpenAiCompatibleChatModel reasoningChatModel(String apiKey, String modelName) {
        return OpenAiCompatibleChatModel.builder()
                .baseUrl("https://api.deepseek.com/v1")
                .apiKey(apiKey)
                .modelName(modelName)
                .additionalParameters(java.util.Map.of("thinking", java.util.Map.of("type", "enabled")))
                .preserveReasoningContent(true)
                .build();
    }
}
