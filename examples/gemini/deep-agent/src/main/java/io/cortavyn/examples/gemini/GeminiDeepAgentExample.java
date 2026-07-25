package io.cortavyn.examples.gemini;
import io.cortavyn.examples.deep.ProviderDeepAgentExample;
import io.cortavyn.provider.gemini.GeminiChatModel;
/** Runs the reviewed Deep Agent workflow through Gemini. */
public final class GeminiDeepAgentExample { private GeminiDeepAgentExample() { } public static void main(String[] args) { var builder = GeminiChatModel.builder().apiKey(requiredApiKey()); String model = System.getenv("GEMINI_MODEL"); if (model != null && !model.isBlank()) builder.modelName(model); ProviderDeepAgentExample.run("gemini", builder.build(), System.getProperty("example.prompt", "How should a team introduce durable AI agents responsibly?")); } private static String requiredApiKey() { String value = System.getenv("GOOGLE_API_KEY"); if (value == null || value.isBlank()) value = System.getenv("GEMINI_API_KEY"); if (value == null || value.isBlank()) throw new IllegalStateException("GOOGLE_API_KEY or GEMINI_API_KEY must be set"); return value; } }
