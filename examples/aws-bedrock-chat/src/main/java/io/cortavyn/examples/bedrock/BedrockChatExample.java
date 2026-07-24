package io.cortavyn.examples.bedrock;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.provider.bedrock.BedrockChatModel;
import java.util.List;

/** Runs one Bedrock Converse request using the AWS default credential and region provider chains. */
public final class BedrockChatExample {
    private BedrockChatExample() { }
    public static void main(String[] args) {
        String prompt = System.getProperty("example.prompt", "Explain durable agents in one sentence.");
        try (BedrockChatModel model = BedrockChatModel.builder().modelId(requiredEnvironment("AWS_BEDROCK_MODEL")).build()) {
            var response = model.complete(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, prompt))))
                    .toCompletableFuture().join();
            System.out.println(response.message().content());
        }
    }
    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
}
