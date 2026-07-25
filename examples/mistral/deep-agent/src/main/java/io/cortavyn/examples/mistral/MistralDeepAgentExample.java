package io.cortavyn.examples.mistral;
import io.cortavyn.examples.deep.ProviderDeepAgentExample;
import io.cortavyn.provider.mistral.MistralChatModel;
/** Runs the reviewed Deep Agent workflow through Mistral. */
public final class MistralDeepAgentExample { private MistralDeepAgentExample() { } public static void main(String[] args) { var builder = MistralChatModel.builder().apiKey(required("MISTRAL_API_KEY")); String model = System.getenv("MISTRAL_MODEL"); if (model != null && !model.isBlank()) builder.modelName(model); ProviderDeepAgentExample.run("mistral", builder.build(), System.getProperty("example.prompt", "How should a team introduce durable AI agents responsibly?")); } private static String required(String key) { String value = System.getenv(key); if (value == null || value.isBlank()) throw new IllegalStateException(key + " must be set"); return value; } }
