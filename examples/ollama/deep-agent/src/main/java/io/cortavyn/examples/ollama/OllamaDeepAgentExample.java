package io.cortavyn.examples.ollama;
import io.cortavyn.examples.deep.ProviderDeepAgentExample;
import io.cortavyn.provider.ollama.OllamaChatModel;
import java.net.URI;
/** Runs the reviewed Deep Agent workflow through local Ollama. */
public final class OllamaDeepAgentExample { private OllamaDeepAgentExample() { } public static void main(String[] args) { var builder = OllamaChatModel.builder(); String model = System.getenv("OLLAMA_MODEL"); if (model != null && !model.isBlank()) builder.modelName(model); String baseUrl = System.getenv("OLLAMA_BASE_URL"); if (baseUrl != null && !baseUrl.isBlank()) builder.baseUrl(URI.create(baseUrl)); ProviderDeepAgentExample.run("ollama", builder.build(), System.getProperty("example.prompt", "How should a team introduce durable AI agents responsibly?")); } }
