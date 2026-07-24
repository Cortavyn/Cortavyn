package io.cortavyn.provider.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.model.api.ChatResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/**
 * An OpenAI Chat Completions API adapter.
 *
 * <p>The adapter depends only on Cortavyn's portable model API. Provider configuration is supplied
 * through {@link Builder}; no credentials are read from the environment automatically.</p>
 */
public final class OpenAiChatModel implements ChatModel {
    private static final URI DEFAULT_BASE_URL = URI.create("https://api.openai.com/v1/");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient httpClient;
    private final URI chatCompletionsUri;
    private final String apiKey;
    private final String modelName;
    private final Duration timeout;

    private OpenAiChatModel(Builder builder) {
        this.httpClient = builder.httpClient == null ? HttpClient.newHttpClient() : builder.httpClient;
        this.chatCompletionsUri = normalizeBaseUrl(builder.baseUrl).resolve("chat/completions");
        this.apiKey = requireNonBlank(builder.apiKey, "apiKey");
        this.modelName = requireNonBlank(builder.modelName, "modelName");
        this.timeout = builder.timeout == null ? DEFAULT_TIMEOUT : builder.timeout;
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public CompletionStage<ChatResponse> complete(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        var httpRequest = HttpRequest.newBuilder(chatCompletionsUri)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("User-Agent", "cortavyn-java")
                .POST(HttpRequest.BodyPublishers.ofString(toRequestJson(request)))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::toChatResponse);
    }

    private ChatResponse toChatResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new OpenAiHttpException(response.statusCode(), response.body());
        }
        try {
            JsonNode choice = JSON.readTree(response.body()).path("choices").path(0);
            @Nullable String content = choice.path("message").path("content").textValue();
            if (content == null) {
                throw new OpenAiResponseException("OpenAI returned no assistant message content");
            }
            return new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, content));
        } catch (JsonProcessingException exception) {
            throw new OpenAiResponseException("OpenAI returned an invalid JSON response", exception);
        }
    }

    String toRequestJson(ChatRequest request) {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", modelName);
        ArrayNode messages = root.putArray("messages");
        for (ChatMessage message : request.messages()) {
            if (message.role() == ChatMessageRole.TOOL) {
                throw new IllegalArgumentException("TOOL messages require tool-call identifiers and are not supported yet");
            }
            ObjectNode wireMessage = messages.addObject();
            wireMessage.put("role", message.role().name().toLowerCase(Locale.ROOT));
            wireMessage.put("content", message.content());
        }
        try {
            return JSON.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize OpenAI request", exception);
        }
    }

    private static URI normalizeBaseUrl(@Nullable URI baseUrl) {
        URI resolved = baseUrl == null ? DEFAULT_BASE_URL : baseUrl;
        return URI.create(resolved.toString().endsWith("/") ? resolved.toString() : resolved + "/");
    }

    private static String requireNonBlank(@Nullable String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public static final class Builder {
        private @Nullable HttpClient httpClient;
        private @Nullable URI baseUrl;
        private @Nullable String apiKey;
        private @Nullable String modelName;
        private @Nullable Duration timeout;

        private Builder() {
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
            return this;
        }

        public Builder baseUrl(URI baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OpenAiChatModel build() {
            return new OpenAiChatModel(this);
        }
    }
}
