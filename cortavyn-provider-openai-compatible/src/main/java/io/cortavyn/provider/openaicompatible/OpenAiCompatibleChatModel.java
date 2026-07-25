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
import io.cortavyn.model.api.ReasoningContent;
import io.cortavyn.model.api.TokenUsage;
import io.cortavyn.model.api.ToolCall;
import io.cortavyn.model.api.ToolDefinition;
import io.cortavyn.model.api.StructuredOutputChatModel;
import io.cortavyn.model.api.StructuredOutputSchema;
import io.cortavyn.model.api.StreamingChatModel;
import io.cortavyn.model.api.ChatStreamEvent;
import io.cortavyn.model.api.ChatStreamPublishers;
import io.cortavyn.model.api.OpenAiChatStreamAccumulator;
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
import java.util.concurrent.Flow.Publisher;
import org.jspecify.annotations.Nullable;

/** Portable adapter for endpoints implementing OpenAI's Chat Completions wire format. */
public final class OpenAiCompatibleChatModel implements StructuredOutputChatModel, StreamingChatModel {
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
    private final Map<String, Object> additionalParameters;
    private final boolean preserveReasoningContent;

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
        additionalParameters = Map.copyOf(b.additionalParameters);
        preserveReasoningContent = b.preserveReasoningContent;
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        if (temperature != null && (temperature < 0 || temperature > 2)) throw new IllegalArgumentException("temperature must be in [0.0, 2.0]");
        if (maxTokens != null && maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be positive");
    }
    public static Builder builder() { return new Builder(); }
    @Override public CompletionStage<ChatResponse> complete(ChatRequest request) { return completeInternal(request, null); }
    @Override public Publisher<ChatStreamEvent> stream(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null"); var accumulator = new OpenAiChatStreamAccumulator();
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout).header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json").header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(toStreamRequestJson(request)));
        headers.forEach(builder::header);
        return ChatStreamPublishers.fromLines(httpClient, builder.build(), response -> new OpenAiCompatibleHttpException(response.statusCode(), ""), accumulator::accept, accumulator::complete);
    }
    @Override public CompletionStage<ChatResponse> complete(ChatRequest request, StructuredOutputSchema schema) {
        return completeInternal(request, Objects.requireNonNull(schema, "schema must not be null"));
    }
    private CompletionStage<ChatResponse> completeInternal(ChatRequest request, @Nullable StructuredOutputSchema schema) {
        Objects.requireNonNull(request, "request must not be null");
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toRequestJson(request, schema)));
        headers.forEach(builder::header);
        return httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString()).thenApply(this::toResponse);
    }
    String toRequestJson(ChatRequest request) {
        return toRequestJson(request, null);
    }
    String toStreamRequestJson(ChatRequest request) { try { ObjectNode root = (ObjectNode) JSON.readTree(toRequestJson(request)); root.put("stream", true); return JSON.writeValueAsString(root); } catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize streaming request", exception); } }
    String toRequestJson(ChatRequest request, @Nullable StructuredOutputSchema schema) {
        ObjectNode root = JSON.createObjectNode().put("model", modelName);
        if (temperature != null) root.put("temperature", temperature);
        if (maxTokens != null) root.put("max_tokens", maxTokens);
        if (!request.tools().isEmpty()) { ArrayNode tools = root.putArray("tools"); for (ToolDefinition tool : request.tools()) { ObjectNode function = tools.addObject().put("type", "function").putObject("function"); function.put("name", tool.name()); function.put("description", tool.description()); function.putPOJO("parameters", tool.inputSchema()); } }
        if (schema != null) root.putObject("response_format").put("type", "json_schema").putObject("json_schema").put("name", schema.name()).put("strict", schema.strict()).putPOJO("schema", schema.jsonSchema());
        additionalParameters.forEach(root::putPOJO);
        ArrayNode messages = root.putArray("messages");
        for (ChatMessage message : request.messages()) {
            ObjectNode wire = messages.addObject().put("role", message.role().name().toLowerCase(Locale.ROOT)).put("content", message.content());
            if (message.role() == ChatMessageRole.TOOL) wire.put("tool_call_id", message.toolCallId());
            if (!message.toolCalls().isEmpty()) { ArrayNode calls = wire.putArray("tool_calls"); for (ToolCall call : message.toolCalls()) calls.addObject().put("id", call.id()).put("type", "function").putObject("function").put("name", call.name()).put("arguments", JSON.valueToTree(call.arguments()).toString()); }
            if (preserveReasoningContent && message.role() == ChatMessageRole.ASSISTANT) {
                message.contentBlocks().stream()
                        .filter(ReasoningContent.class::isInstance)
                        .map(ReasoningContent.class::cast)
                        .map(ReasoningContent::text)
                        .filter(reasoning -> !reasoning.isBlank())
                        .findFirst()
                        .ifPresent(reasoning -> wire.put("reasoning_content", reasoning));
            }
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
            List<io.cortavyn.model.api.ChatContent> blocks = new java.util.ArrayList<>(); blocks.add(new io.cortavyn.model.api.TextContent(content)); @Nullable String reasoning = message.path("reasoning_content").textValue(); if (reasoning != null) blocks.add(new io.cortavyn.model.api.ReasoningContent(reasoning));
            return new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, content, blocks, null, toolCalls), new ChatResponseMetadata(root.path("model").textValue(), response.headers().firstValue("x-request-id").orElse(null), choice.path("finish_reason").textValue(), tokens), Map.of());
        } catch (JsonProcessingException e) { throw new OpenAiCompatibleResponseException("Compatible endpoint returned invalid JSON", e); }
    }
    private static String requireNonBlank(@Nullable String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank"); return value; }
    public static final class Builder {
        private @Nullable HttpClient httpClient; private @Nullable String baseUrl; private @Nullable String apiKey; private @Nullable String modelName;
        private @Nullable Duration timeout; private @Nullable Double temperature; private @Nullable Integer maxTokens; private Map<String, String> headers = Map.of();
        private Map<String, Object> additionalParameters = Map.of(); private boolean preserveReasoningContent;
        private Builder() { }
        public Builder httpClient(HttpClient value) { httpClient = Objects.requireNonNull(value); return this; }
        public Builder baseUrl(String value) { baseUrl = value; return this; }
        public Builder apiKey(String value) { apiKey = value; return this; }
        public Builder modelName(String value) { modelName = value; return this; }
        public Builder timeout(Duration value) { timeout = value; return this; }
        public Builder temperature(double value) { temperature = value; return this; }
        public Builder maxTokens(int value) { maxTokens = value; return this; }
        public Builder headers(Map<String, String> value) { headers = Map.copyOf(value); return this; }
        public Builder additionalParameters(Map<String, Object> value) { additionalParameters = Map.copyOf(value); return this; }
        /** Replays assistant reasoning on subsequent requests for compatible APIs that require it. */
        public Builder preserveReasoningContent(boolean value) { preserveReasoningContent = value; return this; }
        public OpenAiCompatibleChatModel build() { return new OpenAiCompatibleChatModel(this); }
    }
}
