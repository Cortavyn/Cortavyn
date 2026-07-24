package io.cortavyn.provider.anthropic;

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
import io.cortavyn.model.api.TokenUsage;
import io.cortavyn.model.api.ToolCall;
import io.cortavyn.model.api.ToolDefinition;
import io.cortavyn.model.api.ReasoningContent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/** An Anthropic Messages API adapter following LangChain's system-message separation. */
public final class AnthropicChatModel implements ChatModel {
    private static final URI DEFAULT_BASE_URL = URI.create("https://api.anthropic.com/v1/");
    private static final String DEFAULT_MODEL_NAME = "claude-sonnet-4-5";
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> RESERVED_PARAMETERS = Set.of(
            "model", "max_tokens", "system", "messages", "temperature", "top_k", "top_p", "stop_sequences");

    private final HttpClient httpClient;
    private final URI messagesUri;
    private final String apiKey;
    private final String modelName;
    private final int maxTokens;
    private final Duration timeout;
    private final @Nullable Double temperature;
    private final @Nullable Integer topK;
    private final @Nullable Double topP;
    private final List<String> stopSequences;
    private final Map<String, Object> additionalParameters;

    private AnthropicChatModel(Builder builder) {
        this.httpClient = builder.httpClient == null ? HttpClient.newHttpClient() : builder.httpClient;
        this.messagesUri = normalizeBaseUrl(builder.baseUrl).resolve("messages");
        this.apiKey = requireNonBlank(builder.apiKey, "apiKey");
        this.modelName = builder.modelName == null ? DEFAULT_MODEL_NAME : requireNonBlank(builder.modelName, "modelName");
        this.maxTokens = builder.maxTokens == null ? DEFAULT_MAX_TOKENS : builder.maxTokens;
        this.timeout = builder.timeout == null ? DEFAULT_TIMEOUT : builder.timeout;
        this.temperature = builder.temperature;
        this.topK = builder.topK;
        this.topP = builder.topP;
        this.stopSequences = builder.stopSequences == null ? List.of() : List.copyOf(builder.stopSequences);
        this.additionalParameters = Map.copyOf(builder.additionalParameters);
        if (maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be positive");
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        if (temperature != null && temperature < 0) throw new IllegalArgumentException("temperature must not be negative");
        if (topK != null && topK <= 0) throw new IllegalArgumentException("topK must be positive");
        if (topP != null && (topP < 0 || topP > 1)) throw new IllegalArgumentException("topP must be in [0.0, 1.0]");
        additionalParameters.keySet().forEach(key -> { if (RESERVED_PARAMETERS.contains(key)) throw new IllegalArgumentException("additionalParameters must not override " + key); });
    }

    public static Builder builder() { return new Builder(); }

    @Override
    public CompletionStage<ChatResponse> complete(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        HttpRequest httpRequest = HttpRequest.newBuilder(messagesUri)
                .timeout(timeout).header("x-api-key", apiKey).header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .header("User-Agent", "cortavyn-java").POST(HttpRequest.BodyPublishers.ofString(toRequestJson(request))).build();
        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).thenApply(this::toChatResponse);
    }

    String toRequestJson(ChatRequest request) {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", modelName); root.put("max_tokens", maxTokens);
        if (temperature != null) root.put("temperature", temperature);
        if (topK != null) root.put("top_k", topK);
        if (topP != null) root.put("top_p", topP);
        if (!stopSequences.isEmpty()) root.putPOJO("stop_sequences", stopSequences);
        if (!request.tools().isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (ToolDefinition tool : request.tools()) {
                ObjectNode node = tools.addObject(); node.put("name", tool.name()); node.put("description", tool.description()); node.putPOJO("input_schema", tool.inputSchema());
            }
        }
        additionalParameters.forEach(root::putPOJO);
        StringBuilder system = new StringBuilder();
        ArrayNode messages = root.putArray("messages");
        for (ChatMessage message : request.messages()) {
            if (message.role() == ChatMessageRole.SYSTEM) { if (!system.isEmpty()) system.append('\n'); system.append(message.content()); continue; }
            ObjectNode wireMessage = messages.addObject();
            wireMessage.put("role", message.role() == ChatMessageRole.ASSISTANT ? "assistant" : "user");
            if (message.role() == ChatMessageRole.TOOL) {
                ArrayNode blocks = wireMessage.putArray("content");
                blocks.addObject().put("type", "tool_result").put("tool_use_id", message.toolCallId()).put("content", message.content());
            } else if (message.role() == ChatMessageRole.ASSISTANT && message.contentBlocks().stream().anyMatch(io.cortavyn.model.api.ReasoningContent.class::isInstance)) {
                ArrayNode blocks = wireMessage.putArray("content");
                for (io.cortavyn.model.api.ChatContent block : message.contentBlocks()) {
                    if (block instanceof io.cortavyn.model.api.ReasoningContent reasoning) {
                        ObjectNode thinking = blocks.addObject().put("type", "thinking").put("thinking", reasoning.text());
                        Object signature = reasoning.providerState().get("signature"); if (signature instanceof String value && !value.isBlank()) thinking.put("signature", value);
                    } else if (block instanceof io.cortavyn.model.api.TextContent text) blocks.addObject().put("type", "text").put("text", text.text());
                }
            } else wireMessage.put("content", message.content());
        }
        if (!system.isEmpty()) root.put("system", system.toString());
        if (messages.isEmpty()) throw new IllegalArgumentException("Anthropic requires at least one non-system message");
        try { return JSON.writeValueAsString(root); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize Anthropic request", exception); }
    }

    private ChatResponse toChatResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new AnthropicHttpException(response.statusCode(), response.body());
        try {
            JsonNode root = JSON.readTree(response.body()); JsonNode content = root.path("content");
            StringBuilder text = new StringBuilder();
            List<ToolCall> toolCalls = new java.util.ArrayList<>();
            List<io.cortavyn.model.api.ChatContent> blocks = new java.util.ArrayList<>();
            for (JsonNode block : content) { @Nullable String value = block.path("text").textValue(); if (value != null) text.append(value); if ("thinking".equals(block.path("type").asText())) { @Nullable String thinking = block.path("thinking").textValue(); if (thinking != null) blocks.add(new ReasoningContent(thinking, Map.of("signature", block.path("signature").asText()))); } if ("tool_use".equals(block.path("type").asText())) toolCalls.add(new ToolCall(block.path("id").asText(), block.path("name").asText(), JSON.convertValue(block.path("input"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }))); }
            if (text.isEmpty() && toolCalls.isEmpty()) throw new AnthropicResponseException("Anthropic returned neither assistant text nor tool calls");
            JsonNode usage = root.path("usage"); TokenUsage tokenUsage = usage.isMissingNode() ? null : new TokenUsage(usage.path("input_tokens").asInt(), usage.path("output_tokens").asInt(), usage.path("input_tokens").asInt() + usage.path("output_tokens").asInt());
            blocks.addFirst(new io.cortavyn.model.api.TextContent(text.toString()));
            return new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, text.toString(), blocks, null, toolCalls), new ChatResponseMetadata(root.path("model").textValue(), response.headers().firstValue("request-id").orElse(null), root.path("stop_reason").textValue(), tokenUsage), Map.of());
        } catch (JsonProcessingException exception) { throw new AnthropicResponseException("Anthropic returned an invalid JSON response", exception); }
    }

    private static URI normalizeBaseUrl(@Nullable URI baseUrl) { URI resolved = baseUrl == null ? DEFAULT_BASE_URL : baseUrl; return URI.create(resolved.toString().endsWith("/") ? resolved.toString() : resolved + "/"); }
    private static String requireNonBlank(@Nullable String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank"); return value; }

    public static final class Builder {
        private @Nullable HttpClient httpClient; private @Nullable URI baseUrl; private @Nullable String apiKey; private @Nullable String modelName;
        private @Nullable Integer maxTokens; private @Nullable Duration timeout; private @Nullable Double temperature; private @Nullable Integer topK; private @Nullable Double topP;
        private @Nullable List<String> stopSequences; private Map<String, Object> additionalParameters = Map.of();
        private Builder() { }
        public Builder httpClient(HttpClient value) { httpClient = Objects.requireNonNull(value); return this; }
        public Builder baseUrl(URI value) { baseUrl = Objects.requireNonNull(value); return this; }
        public Builder apiKey(String value) { apiKey = value; return this; }
        public Builder modelName(String value) { modelName = value; return this; }
        public Builder maxTokens(int value) { maxTokens = value; return this; }
        public Builder timeout(Duration value) { timeout = value; return this; }
        public Builder temperature(double value) { temperature = value; return this; }
        public Builder topK(int value) { topK = value; return this; }
        public Builder topP(double value) { topP = value; return this; }
        public Builder stopSequences(List<String> value) { stopSequences = List.copyOf(value); return this; }
        public Builder additionalParameters(Map<String, Object> value) { additionalParameters = Map.copyOf(value); return this; }
        public AnthropicChatModel build() { return new AnthropicChatModel(this); }
    }
}
