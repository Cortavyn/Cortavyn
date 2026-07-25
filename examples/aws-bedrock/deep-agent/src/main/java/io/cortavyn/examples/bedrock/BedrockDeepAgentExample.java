package io.cortavyn.examples.bedrock;
import io.cortavyn.examples.deep.ProviderDeepAgentExample;
import io.cortavyn.provider.bedrock.BedrockChatModel;
/** Runs the reviewed Deep Agent workflow through Bedrock Converse. */
public final class BedrockDeepAgentExample { private BedrockDeepAgentExample() { } public static void main(String[] args) { try (BedrockChatModel model = BedrockChatModel.builder().modelId(required("AWS_BEDROCK_MODEL")).build()) { ProviderDeepAgentExample.run("aws-bedrock", model, System.getProperty("example.prompt", "How should a team introduce durable AI agents responsibly?")); } } private static String required(String key) { String value = System.getenv(key); if (value == null || value.isBlank()) throw new IllegalStateException(key + " must be set"); return value; } }
