package io.cortavyn.provider.openaicompatible;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.model.api.ChatResponse;
import io.cortavyn.model.api.ChatResponseMetadata;
import io.cortavyn.model.api.TokenUsage;
import io.cortavyn.model.api.ToolCall;
import io.cortavyn.model.api.ToolDefinition;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/** Portable adapter for endpoints implementing OpenAI's Chat Completions wire format. */
public final class OpenAiCompatibleChatModel implements ChatModel {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private final HttpClient httpClient;
    private final URI uri;
    private final String apiKey;
    private final String modelName;
    private final Duration timeout;
    private final @Nullable Double temperature;
    private final @Nullable Integer maxTokens;
    private final Map<String, String> headers;

    private OpenAiCompatibleChatModel(Builder b) {
        httpClient = b.httpClient == null ? HttpClient.newHttpClient() : b.httpClient;
        String baseUrl = requireNonBlank(b.baseUrl, "baseUrl");
        uri = URI.create(baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions");
        apiKey = requireNonBlank(b.apiKey, "apiKey");
        modelName = requireNonBlank(b.modelName, "modelName");
        timeout = b.timeout == null ? DEFAULT_TIMEOUT : b.timeout;
        temperature = b.temperature;
        maxTokens = b.maxTokens;
        headers = Map.copyOf(b.headers);
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        if (temperature != null && (temperature < 0 || temperature > 2)) throw new IllegalArgumentException("temperature must be in [0.0, 2.0]");
        if (maxTokens != null && maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be positive");
    }
    public static Builder builder() { return new Builder(); }
    @Override public CompletionStage<ChatResponse> complete(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toRequestJson(request)));
        headers.forEach(builder::header);
        return httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString()).thenApply(this::toResponse);
    }
    String toRequestJson(ChatRequest request) {
        ObjectNode root = JSON.createObjectNode().put("model", modelName);
        if (temperature != null) root.put("temperature", temperature);
        if (maxTokens != null) root.put("max_tokens", maxTokens);
        if (!request.tools().isEmpty()) { ArrayNode tools = root.putArray("tools"); for (ToolDefinition tool : request.tools()) { ObjectNode function = tools.addObject().put("type", "function").putObject("function"); function.put("name", tool.name()); function.put("description", tool.description()); function.putPOJO("parameters", tool.inputSchema()); } }
        ArrayNode messages = root.putArray("messages");
        for (ChatMessage message : request.messages()) {
            ObjectNode wire = messages.addObject().put("role", message.role().name().toLowerCase(Locale.ROOT)).put("content", message.content());
            if (message.role() == ChatMessageRole.TOOL) wire.put("tool_call_id", message.toolCallId());
        }
        try { return JSON.writeValueAsString(root); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Unable to serialize chat request", e); }
    }
    private ChatResponse toResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new OpenAiCompatibleHttpException(response.statusCode(), response.body());
        try {
            var root = JSON.readTree(response.body()); var choice = root.path("choices").path(0); var message = choice.path("message");
            @Nullable String content = message.path("content").textValue(); if (content == null) content = "";
            List<ToolCall> toolCalls = new java.util.ArrayList<>();
            for (var call : message.path("tool_calls")) { String arguments = call.path("function").path("arguments").asText("{}"); try { toolCalls.add(new ToolCall(call.path("id").asText(), call.path("function").path("name").asText(), JSON.readValue(arguments, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }))); } catch (JsonProcessingException exception) { throw new OpenAiCompatibleResponseException("Compatible endpoint returned invalid tool-call arguments", exception); } }
            if (content.isEmpty() && toolCalls.isEmpty()) throw new OpenAiCompatibleResponseException("Compatible endpoint returned no assistant text or tool calls");
            var usage = root.path("usage"); TokenUsage tokens = usage.isMissingNode() ? null : new TokenUsage(usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt(), usage.path("total_tokens").asInt());
            return new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, content, List.of(new io.cortavyn.model.api.TextContent(content)), null, toolCalls), new ChatResponseMetadata(root.path("model").textValue(), response.headers().firstValue("x-request-id").orElse(null), choice.path("finish_reason").textValue(), tokens), Map.of());
        } catch (JsonProcessingException e) { throw new OpenAiCompatibleResponseException("Compatible endpoint returned invalid JSON", e); }
    }
    private static String requireNonBlank(@Nullable String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank"); return value; }
    public static final class Builder {
        private @Nullable HttpClient httpClient; private @Nullable String baseUrl; private @Nullable String apiKey; private @Nullable String modelName;
        private @Nullable Duration timeout; private @Nullable Double temperature; private @Nullable Integer maxTokens; private Map<String, String> headers = Map.of();
        private Builder() { }
        public Builder httpClient(HttpClient value) { httpClient = Objects.requireNonNull(value); return this; }
        public Builder baseUrl(String value) { baseUrl = value; return this; }
        public Builder apiKey(String value) { apiKey = value; return this; }
        public Builder modelName(String value) { modelName = value; return this; }
        public Builder timeout(Duration value) { timeout = value; return this; }
        public Builder temperature(double value) { temperature = value; return this; }
        public Builder maxTokens(int value) { maxTokens = value; return this; }
        public Builder headers(Map<String, String> value) { headers = Map.copyOf(value); return this; }
        public OpenAiCompatibleChatModel build() { return new OpenAiCompatibleChatModel(this); }
    }
}
