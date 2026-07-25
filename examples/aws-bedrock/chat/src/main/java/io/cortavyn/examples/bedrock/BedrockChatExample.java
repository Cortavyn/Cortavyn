package io.cortavyn.examples.bedrock;

import io.cortavyn.examples.graph.ResearchConversationExample;
import io.cortavyn.provider.bedrock.BedrockChatModel;

/** Runs a two-turn research and review conversation through Bedrock Converse. */
public final class BedrockChatExample {
    private BedrockChatExample() { }
    public static void main(String[] args) {
        String prompt = System.getProperty("example.prompt", "What trade-offs should a team consider when introducing durable AI agents?");
        try (BedrockChatModel model = BedrockChatModel.builder().modelId(requiredEnvironment("AWS_BEDROCK_MODEL")).build()) {
            ResearchConversationExample.run("aws-bedrock", model, prompt);
        }
    }
    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
}
