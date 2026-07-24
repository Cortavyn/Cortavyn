package io.cortavyn.provider.mistral;

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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/** A Mistral chat-completions adapter with Mistral's default generation parameters. */
public final class MistralChatModel implements ChatModel {
    private static final URI DEFAULT_BASE_URL = URI.create("https://api.mistral.ai/v1/");
    private static final String DEFAULT_MODEL_NAME = "mistral-small";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient httpClient;
    private final URI chatCompletionsUri;
    private final String apiKey;
    private final String modelName;
    private final Duration timeout;
    private final double temperature;
    private final double topP;
    private final @Nullable Integer maxTokens;
    private final List<String> stopSequences;
    private final @Nullable Integer randomSeed;
    private final @Nullable Boolean safePrompt;
    private final Map<String, Object> additionalParameters;

    private MistralChatModel(Builder builder) {
        this.httpClient = builder.httpClient == null ? HttpClient.newHttpClient() : builder.httpClient;
        this.chatCompletionsUri = normalizeBaseUrl(builder.baseUrl).resolve("chat/completions");
        this.apiKey = requireNonBlank(builder.apiKey, "apiKey");
        this.modelName = builder.modelName == null ? DEFAULT_MODEL_NAME : requireNonBlank(builder.modelName, "modelName");
        this.timeout = builder.timeout == null ? DEFAULT_TIMEOUT : builder.timeout;
        this.temperature = builder.temperature == null ? 0.7 : builder.temperature;
        this.topP = builder.topP == null ? 1.0 : builder.topP;
        this.maxTokens = builder.maxTokens;
        this.stopSequences = builder.stopSequences == null ? List.of() : List.copyOf(builder.stopSequences);
        this.randomSeed = builder.randomSeed;
        this.safePrompt = builder.safePrompt;
        this.additionalParameters = Map.copyOf(builder.additionalParameters);
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        if (temperature < 0 || temperature > 1) throw new IllegalArgumentException("temperature must be in [0.0, 1.0]");
        if (topP < 0 || topP > 1) throw new IllegalArgumentException("topP must be in [0.0, 1.0]");
        if (maxTokens != null && maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be positive");
        if (stopSequences.size() > 4) throw new IllegalArgumentException("Mistral supports at most four stop sequences");
        additionalParameters.keySet().forEach(key -> {
            if (RESERVED_PARAMETERS.contains(key)) throw new IllegalArgumentException("additionalParameters must not override " + key);
        });
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public CompletionStage<ChatResponse> complete(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        HttpRequest httpRequest = HttpRequest.newBuilder(chatCompletionsUri)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "cortavyn-java")
                .POST(HttpRequest.BodyPublishers.ofString(toRequestJson(request)))
                .build();
        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).thenApply(this::toChatResponse);
    }

    String toRequestJson(ChatRequest request) {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", modelName);
        root.put("temperature", temperature);
        root.put("top_p", topP);
        if (maxTokens != null) root.put("max_tokens", maxTokens);
        if (!stopSequences.isEmpty()) root.putPOJO("stop", stopSequences);
        if (randomSeed != null) root.put("random_seed", randomSeed);
        if (safePrompt != null) root.put("safe_prompt", safePrompt);
        additionalParameters.forEach(root::putPOJO);
        if (!request.tools().isEmpty()) { ArrayNode tools = root.putArray("tools"); for (ToolDefinition tool : request.tools()) { ObjectNode function = tools.addObject().put("type", "function").putObject("function"); function.put("name", tool.name()); function.put("description", tool.description()); function.putPOJO("parameters", tool.inputSchema()); } }

        ArrayNode messages = root.putArray("messages");
        for (ChatMessage message : request.messages()) {
            ObjectNode wireMessage = messages.addObject();
            wireMessage.put("role", message.role().name().toLowerCase(Locale.ROOT));
            wireMessage.put("content", message.content());
            if (message.role() == ChatMessageRole.TOOL) wireMessage.put("tool_call_id", message.toolCallId());
        }
        try {
            return JSON.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Mistral request", exception);
        }
    }

    private ChatResponse toChatResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new MistralHttpException(response.statusCode(), response.body());
        }
        try {
            JsonNode root = JSON.readTree(response.body()); JsonNode choice = root.path("choices").path(0); JsonNode message = choice.path("message"); @Nullable String content = message.path("content").textValue(); if (content == null) content = "";
            List<ToolCall> calls = new java.util.ArrayList<>(); for (JsonNode call : message.path("tool_calls")) { String arguments = call.path("function").path("arguments").asText("{}"); calls.add(new ToolCall(call.path("id").asText(), call.path("function").path("name").asText(), JSON.readValue(arguments, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }))); }
            if (content.isEmpty() && calls.isEmpty()) throw new MistralResponseException("Mistral returned no assistant text or tool calls");
            JsonNode usage = root.path("usage"); TokenUsage tokens = usage.isMissingNode() ? null : new TokenUsage(usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt(), usage.path("total_tokens").asInt());
            List<io.cortavyn.model.api.ChatContent> blocks = new java.util.ArrayList<>(); blocks.add(new io.cortavyn.model.api.TextContent(content)); @Nullable String reasoning = message.path("reasoning_content").textValue(); if (reasoning != null) blocks.add(new ReasoningContent(reasoning));
            return new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, content, blocks, null, calls), new ChatResponseMetadata(root.path("model").textValue(), response.headers().firstValue("x-request-id").orElse(null), choice.path("finish_reason").textValue(), tokens), Map.of());
        } catch (JsonProcessingException exception) {
            throw new MistralResponseException("Mistral returned an invalid JSON response", exception);
        }
    }

    private static URI normalizeBaseUrl(@Nullable URI baseUrl) {
        URI resolved = baseUrl == null ? DEFAULT_BASE_URL : baseUrl;
        return URI.create(resolved.toString().endsWith("/") ? resolved.toString() : resolved + "/");
    }

    private static String requireNonBlank(@Nullable String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private static final java.util.Set<String> RESERVED_PARAMETERS = java.util.Set.of(
            "model", "messages", "temperature", "top_p", "max_tokens", "stop", "random_seed", "safe_prompt");

    public static final class Builder {
        private @Nullable HttpClient httpClient;
        private @Nullable URI baseUrl;
        private @Nullable String apiKey;
        private @Nullable String modelName;
        private @Nullable Duration timeout;
        private @Nullable Double temperature;
        private @Nullable Double topP;
        private @Nullable Integer maxTokens;
        private @Nullable List<String> stopSequences;
        private @Nullable Integer randomSeed;
        private @Nullable Boolean safePrompt;
        private Map<String, Object> additionalParameters = Map.of();

        private Builder() {
        }

        public Builder httpClient(HttpClient httpClient) { this.httpClient = Objects.requireNonNull(httpClient); return this; }
        public Builder baseUrl(URI baseUrl) { this.baseUrl = Objects.requireNonNull(baseUrl); return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder modelName(String modelName) { this.modelName = modelName; return this; }
        public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }
        public Builder temperature(double temperature) { this.temperature = temperature; return this; }
        public Builder topP(double topP) { this.topP = topP; return this; }
        public Builder maxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
        public Builder stopSequences(List<String> stopSequences) { this.stopSequences = List.copyOf(stopSequences); return this; }
        public Builder randomSeed(int randomSeed) { this.randomSeed = randomSeed; return this; }
        public Builder safePrompt(boolean safePrompt) { this.safePrompt = safePrompt; return this; }
        public Builder additionalParameters(Map<String, Object> additionalParameters) { this.additionalParameters = Map.copyOf(additionalParameters); return this; }
        public MistralChatModel build() { return new MistralChatModel(this); }
    }
}
