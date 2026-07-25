package io.cortavyn.examples.anthropic;
import io.cortavyn.examples.deep.ProviderDeepAgentExample;
import io.cortavyn.provider.anthropic.AnthropicChatModel;
/** Runs the reviewed Deep Agent workflow through Anthropic. */
public final class AnthropicDeepAgentExample { private AnthropicDeepAgentExample() { } public static void main(String[] args) { var builder = AnthropicChatModel.builder().apiKey(required("ANTHROPIC_API_KEY")); String model = System.getenv("ANTHROPIC_MODEL"); if (model != null && !model.isBlank()) builder.modelName(model); ProviderDeepAgentExample.run("anthropic", builder.build(), System.getProperty("example.prompt", "How should a team introduce durable AI agents responsibly?")); } private static String required(String key) { String value = System.getenv(key); if (value == null || value.isBlank()) throw new IllegalStateException(key + " must be set"); return value; } }
