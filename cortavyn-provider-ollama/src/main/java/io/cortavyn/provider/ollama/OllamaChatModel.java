package io.cortavyn.provider.ollama;

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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/** An Ollama {@code /api/chat} adapter using a non-streaming response per portable request. */
public final class OllamaChatModel implements ChatModel {
    private static final URI DEFAULT_BASE_URL = URI.create("http://localhost:11434/");
    private static final String DEFAULT_MODEL_NAME = "llama3.2";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient httpClient; private final URI chatUri; private final String modelName; private final Duration timeout;
    private final Map<String, Object> options; private final @Nullable Boolean think; private final @Nullable String keepAlive;

    private OllamaChatModel(Builder builder) {
        httpClient = builder.httpClient == null ? HttpClient.newHttpClient() : builder.httpClient;
        chatUri = normalizeBaseUrl(builder.baseUrl).resolve("api/chat");
        modelName = builder.modelName == null ? DEFAULT_MODEL_NAME : requireNonBlank(builder.modelName, "modelName");
        timeout = builder.timeout == null ? DEFAULT_TIMEOUT : builder.timeout;
        options = Map.copyOf(builder.options); think = builder.think; keepAlive = builder.keepAlive;
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
    }

    public static Builder builder() { return new Builder(); }

    @Override public CompletionStage<ChatResponse> complete(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        HttpRequest httpRequest = HttpRequest.newBuilder(chatUri).timeout(timeout).header("Content-Type", "application/json").header("Accept", "application/json")
                .header("User-Agent", "cortavyn-java").POST(HttpRequest.BodyPublishers.ofString(toRequestJson(request))).build();
        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).thenApply(this::toChatResponse);
    }

    String toRequestJson(ChatRequest request) {
        ObjectNode root = JSON.createObjectNode(); root.put("model", modelName); root.put("stream", false);
        if (!options.isEmpty()) root.putPOJO("options", options);
        if (think != null) root.put("think", think);
        if (keepAlive != null) root.put("keep_alive", keepAlive);
        ArrayNode messages = root.putArray("messages");
        for (ChatMessage message : request.messages()) {
            if (message.role() == ChatMessageRole.TOOL) throw new IllegalArgumentException("TOOL messages require tool-call metadata and are not supported yet");
            ObjectNode wireMessage = messages.addObject();
            wireMessage.put("role", switch (message.role()) { case SYSTEM -> "system"; case USER -> "user"; case ASSISTANT -> "assistant"; case TOOL -> throw new AssertionError(); });
            wireMessage.put("content", message.content());
        }
        try { return JSON.writeValueAsString(root); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize Ollama request", exception); }
    }

    private ChatResponse toChatResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new OllamaHttpException(response.statusCode(), response.body());
        try {
            @Nullable String content = JSON.readTree(response.body()).path("message").path("content").textValue();
            if (content == null) throw new OllamaResponseException("Ollama returned no assistant message content");
            return new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, content));
        } catch (JsonProcessingException exception) { throw new OllamaResponseException("Ollama returned an invalid JSON response", exception); }
    }

    private static URI normalizeBaseUrl(@Nullable URI baseUrl) { URI resolved = baseUrl == null ? DEFAULT_BASE_URL : baseUrl; return URI.create(resolved.toString().endsWith("/") ? resolved.toString() : resolved + "/"); }
    private static String requireNonBlank(@Nullable String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank"); return value; }

    public static final class Builder {
        private @Nullable HttpClient httpClient; private @Nullable URI baseUrl; private @Nullable String modelName; private @Nullable Duration timeout;
        private Map<String, Object> options = Map.of(); private @Nullable Boolean think; private @Nullable String keepAlive;
        private Builder() { }
        public Builder httpClient(HttpClient value) { httpClient = Objects.requireNonNull(value); return this; }
        public Builder baseUrl(URI value) { baseUrl = Objects.requireNonNull(value); return this; }
        public Builder modelName(String value) { modelName = value; return this; }
        public Builder timeout(Duration value) { timeout = value; return this; }
        public Builder options(Map<String, Object> value) { options = Map.copyOf(value); return this; }
        public Builder think(boolean value) { think = value; return this; }
        public Builder keepAlive(String value) { keepAlive = requireNonBlank(value, "keepAlive"); return this; }
        public OllamaChatModel build() { return new OllamaChatModel(this); }
    }
}
