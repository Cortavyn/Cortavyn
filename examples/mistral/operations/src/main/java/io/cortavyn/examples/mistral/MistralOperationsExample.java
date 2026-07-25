package io.cortavyn.examples.mistral;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModels;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.model.api.InMemoryChatResponseCache;
import io.cortavyn.model.api.ModelCapability;
import io.cortavyn.model.api.ModelCapabilityRegistry;
import io.cortavyn.model.api.ModelConfiguration;
import io.cortavyn.model.api.ModelFactory;
import io.cortavyn.model.api.ModelMetrics;
import io.cortavyn.model.api.ModelProfile;
import io.cortavyn.model.api.RetryPolicy;
import io.cortavyn.model.api.RetryingChatModel;
import io.cortavyn.provider.mistral.MistralChatModel;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/** Selects and decorates a Mistral model with profile, retry, cache, concurrency, and metrics. */
public final class MistralOperationsExample {
    private MistralOperationsExample() { }

    public static void main(String[] args) {
        var registry = new ModelCapabilityRegistry();
        String modelName = optionalEnvironment("MISTRAL_MODEL", "mistral-small");
        registry.register(new ModelProfile("mistral", modelName,
                Set.of(ModelCapability.STREAMING, ModelCapability.TOOLS, ModelCapability.STRUCTURED_OUTPUT,
                        ModelCapability.IMAGE_INPUT), null));

        var factory = new ModelFactory(registry);
        factory.register(new io.cortavyn.model.api.ModelProviderFactory() {
            @Override public String providerId() { return "mistral"; }
            @Override public MistralChatModel create(ModelConfiguration configuration) {
                return MistralChatModel.builder().apiKey(requiredOption(configuration, "apiKey"))
                        .modelName(configuration.modelName()).build();
            }
        });
        var baseModel = factory.create("mistral", new ModelConfiguration(modelName, Map.of("apiKey", requiredEnvironment("MISTRAL_API_KEY"))));

        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var metrics = new ModelMetrics();
            var model = ChatModels.bounded(ChatModels.observed(ChatModels.cached(
                    new RetryingChatModel(baseModel, RetryPolicy.transientFailures(3, Duration.ofMillis(250), Duration.ofSeconds(2)), scheduler),
                    new InMemoryChatResponseCache()), metrics), 2, executor);
            String prompt = System.getProperty("example.prompt", "Explain durable agents in one sentence.");
            var response = model.complete(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, prompt))))
                    .toCompletableFuture().join();
            System.out.println(response.message().content());
            System.out.printf("calls=%d, inputTokens=%d, outputTokens=%d%n", metrics.calls(), metrics.inputTokens(), metrics.outputTokens());
        } finally {
            scheduler.shutdownNow();
            executor.shutdownNow();
        }
    }

    private static String requiredOption(ModelConfiguration configuration, String name) {
        Object value = configuration.options().get(name);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("configuration option " + name + " must be a non-blank string");
        return text;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
    private static String optionalEnvironment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
