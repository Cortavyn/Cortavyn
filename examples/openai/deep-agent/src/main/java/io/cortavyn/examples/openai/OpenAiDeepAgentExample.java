package io.cortavyn.examples.openai;
import io.cortavyn.examples.deep.ProviderDeepAgentExample;
import io.cortavyn.provider.openai.OpenAiChatModel;
/** Runs the reviewed Deep Agent workflow through OpenAI. */
public final class OpenAiDeepAgentExample { private OpenAiDeepAgentExample() { } public static void main(String[] args) { ProviderDeepAgentExample.run("openai", OpenAiChatModel.builder().apiKey(required("OPENAI_API_KEY")).modelName(System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini")).build(), System.getProperty("example.prompt", "How should a team introduce durable AI agents responsibly?")); } private static String required(String key) { String value = System.getenv(key); if (value == null || value.isBlank()) throw new IllegalStateException(key + " must be set"); return value; } }
