package io.cortavyn.examples.mistral;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.provider.mistral.MistralChatModel;
import java.util.List;

/** Runs one real Mistral chat completion when explicitly invoked through Maven. */
public final class MistralChatExample {
    private MistralChatExample() {
    }

    public static void main(String[] args) {
        String prompt = System.getProperty("example.prompt", "Explain durable agents in one sentence.");
        var builder = MistralChatModel.builder().apiKey(requiredEnvironment("MISTRAL_API_KEY"));
        String modelName = System.getenv("MISTRAL_MODEL");
        if (modelName != null && !modelName.isBlank()) builder.modelName(modelName);

        var response = builder.build()
                .complete(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, prompt))))
                .toCompletableFuture()
                .join();
        System.out.println(response.message().content());
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
}
