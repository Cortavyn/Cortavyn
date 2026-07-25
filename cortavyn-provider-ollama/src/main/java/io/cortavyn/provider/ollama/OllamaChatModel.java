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
import io.cortavyn.model.api.ChatResponseMetadata;
import io.cortavyn.model.api.ReasoningContent;
import io.cortavyn.model.api.TokenUsage;
import io.cortavyn.model.api.ToolCall;
import io.cortavyn.model.api.ToolDefinition;
import io.cortavyn.model.api.StreamingChatModel;
import io.cortavyn.model.api.ChatStreamEvent;
import io.cortavyn.model.api.ChatStreamPublishers;
import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Publisher;
import org.jspecify.annotations.Nullable;

/** An Ollama {@code /api/chat} adapter using a non-streaming response per portable request. */
public final class OllamaChatModel implements ChatModel, StreamingChatModel {
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
    @Override public Publisher<ChatStreamEvent> stream(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null"); var accumulator = new OllamaStreamAccumulator();
        HttpRequest httpRequest = HttpRequest.newBuilder(chatUri).timeout(timeout).header("Content-Type", "application/json").header("Accept", "application/x-ndjson").header("User-Agent", "cortavyn-java").POST(HttpRequest.BodyPublishers.ofString(toStreamRequestJson(request))).build();
        return ChatStreamPublishers.fromLines(httpClient, httpRequest, response -> new OllamaHttpException(response.statusCode(), ""), accumulator::accept, accumulator::complete);
    }

    String toRequestJson(ChatRequest request) {
        ObjectNode root = JSON.createObjectNode(); root.put("model", modelName); root.put("stream", false);
        if (!options.isEmpty()) root.putPOJO("options", options);
        if (think != null) root.put("think", think);
        if (keepAlive != null) root.put("keep_alive", keepAlive);
        if (!request.tools().isEmpty()) { ArrayNode tools = root.putArray("tools"); for (ToolDefinition tool : request.tools()) { ObjectNode function = tools.addObject().put("type", "function").putObject("function"); function.put("name", tool.name()); function.put("description", tool.description()); function.putPOJO("parameters", tool.inputSchema()); } }
        ArrayNode messages = root.putArray("messages");
        for (ChatMessage message : request.messages()) {
            ObjectNode wireMessage = messages.addObject();
            wireMessage.put("role", switch (message.role()) { case SYSTEM -> "system"; case USER -> "user"; case ASSISTANT -> "assistant"; case TOOL -> "tool"; });
            wireMessage.put("content", message.content());
            if (!message.toolCalls().isEmpty()) { ArrayNode calls = wireMessage.putArray("tool_calls"); for (ToolCall call : message.toolCalls()) calls.addObject().putObject("function").put("name", call.name()).putPOJO("arguments", call.arguments()); }
        }
        try { return JSON.writeValueAsString(root); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize Ollama request", exception); }
    }
    String toStreamRequestJson(ChatRequest request) { try { ObjectNode root = (ObjectNode) JSON.readTree(toRequestJson(request)); root.put("stream", true); return JSON.writeValueAsString(root); } catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize Ollama streaming request", exception); } }

    private ChatResponse toChatResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new OllamaHttpException(response.statusCode(), response.body());
        try {
            JsonNode root = JSON.readTree(response.body()); JsonNode message = root.path("message"); @Nullable String content = message.path("content").textValue(); if (content == null) content = "";
            List<ToolCall> calls = new java.util.ArrayList<>(); int index = 0; for (JsonNode call : message.path("tool_calls")) { JsonNode function = call.path("function"); calls.add(new ToolCall("ollama-" + index++, function.path("name").asText(), JSON.convertValue(function.path("arguments"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }))); }
            if (content.isEmpty() && calls.isEmpty()) throw new OllamaResponseException("Ollama returned no assistant text or tool calls");
            List<io.cortavyn.model.api.ChatContent> blocks = new java.util.ArrayList<>(); blocks.add(new io.cortavyn.model.api.TextContent(content)); @Nullable String thinking = message.path("thinking").textValue(); if (thinking != null) blocks.add(new ReasoningContent(thinking));
            TokenUsage usage = root.path("prompt_eval_count").isMissingNode() ? null : new TokenUsage(root.path("prompt_eval_count").asInt(), root.path("eval_count").asInt(), root.path("prompt_eval_count").asInt() + root.path("eval_count").asInt());
            return new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, content, blocks, null, calls), new ChatResponseMetadata(root.path("model").textValue(), null, root.path("done_reason").textValue(), usage), Map.of());
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
