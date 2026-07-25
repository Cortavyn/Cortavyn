package io.cortavyn.examples.azureopenai;
import io.cortavyn.examples.deep.ProviderDeepAgentExample;
import io.cortavyn.provider.azureopenai.AzureOpenAiChatModel;
import java.net.URI;
/** Runs the reviewed Deep Agent workflow through Azure OpenAI. */
public final class AzureOpenAiDeepAgentExample { private AzureOpenAiDeepAgentExample() { } public static void main(String[] args) { var model = AzureOpenAiChatModel.builder().endpoint(URI.create(required("AZURE_OPENAI_ENDPOINT"))).apiKey(required("AZURE_OPENAI_API_KEY")).deploymentName(required("AZURE_OPENAI_DEPLOYMENT")).apiVersion(required("AZURE_OPENAI_API_VERSION")).build(); ProviderDeepAgentExample.run("azure-openai", model, System.getProperty("example.prompt", "How should a team introduce durable AI agents responsibly?")); } private static String required(String key) { String value = System.getenv(key); if (value == null || value.isBlank()) throw new IllegalStateException(key + " must be set"); return value; } }
