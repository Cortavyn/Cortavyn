package io.cortavyn.provider.openrouter;

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

/** An OpenRouter chat-completions adapter with optional application attribution headers. */
public final class OpenRouterChatModel implements ChatModel {
    private static final URI DEFAULT_BASE_URL = URI.create("https://openrouter.ai/api/v1/");
    private static final String DEFAULT_MODEL_NAME = "openrouter/free";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient httpClient;
    private final URI chatCompletionsUri;
    private final String apiKey;
    private final String modelName;
    private final Duration timeout;
    private final @Nullable String siteUrl;
    private final @Nullable String appTitle;
    private final List<String> appCategories;
    private final @Nullable Double temperature;
    private final @Nullable Integer maxTokens;
    private final @Nullable Double topP;
    private final List<String> stopSequences;
    private final Map<String, Object> providerPreferences;
    private final @Nullable String route;
    private final Map<String, Object> reasoning;
    private final List<Map<String, Object>> plugins;
    private final @Nullable String sessionId;
    private final Map<String, Object> trace;

    private OpenRouterChatModel(Builder builder) {
        this.httpClient = builder.httpClient == null ? HttpClient.newHttpClient() : builder.httpClient;
        this.chatCompletionsUri = normalizeBaseUrl(builder.baseUrl).resolve("chat/completions");
        this.apiKey = requireNonBlank(builder.apiKey, "apiKey");
        this.modelName = builder.modelName == null ? DEFAULT_MODEL_NAME : requireNonBlank(builder.modelName, "modelName");
        this.timeout = builder.timeout == null ? DEFAULT_TIMEOUT : builder.timeout;
        this.siteUrl = builder.siteUrl;
        this.appTitle = builder.appTitle;
        this.appCategories = List.copyOf(builder.appCategories);
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.topP = builder.topP;
        this.stopSequences = builder.stopSequences == null ? List.of() : List.copyOf(builder.stopSequences);
        this.providerPreferences = Map.copyOf(builder.providerPreferences);
        this.route = builder.route;
        this.reasoning = Map.copyOf(builder.reasoning);
        this.plugins = List.copyOf(builder.plugins);
        this.sessionId = builder.sessionId;
        this.trace = Map.copyOf(builder.trace);
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        if (temperature != null && (temperature < 0 || temperature > 2)) throw new IllegalArgumentException("temperature must be in [0.0, 2.0]");
        if (maxTokens != null && maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be positive");
        if (topP != null && (topP < 0 || topP > 1)) throw new IllegalArgumentException("topP must be in [0.0, 1.0]");
    }

    public static Builder builder() { return new Builder(); }

    @Override
    public CompletionStage<ChatResponse> complete(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(chatCompletionsUri)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "cortavyn-java")
                .POST(HttpRequest.BodyPublishers.ofString(toRequestJson(request)));
        if (siteUrl != null) requestBuilder.header("HTTP-Referer", siteUrl);
        if (appTitle != null) requestBuilder.header("X-OpenRouter-Title", appTitle);
        if (!appCategories.isEmpty()) requestBuilder.header("X-OpenRouter-Categories", String.join(",", appCategories));
        return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()).thenApply(this::toChatResponse);
    }

    String toRequestJson(ChatRequest request) {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", modelName);
        if (temperature != null) root.put("temperature", temperature);
        if (maxTokens != null) root.put("max_tokens", maxTokens);
        if (topP != null) root.put("top_p", topP);
        if (!stopSequences.isEmpty()) root.putPOJO("stop", stopSequences);
        if (!providerPreferences.isEmpty()) root.putPOJO("provider", providerPreferences);
        if (route != null) root.put("route", route);
        if (!reasoning.isEmpty()) root.putPOJO("reasoning", reasoning);
        if (!plugins.isEmpty()) root.putPOJO("plugins", plugins);
        if (sessionId != null) root.put("session_id", sessionId);
        if (!trace.isEmpty()) root.putPOJO("trace", trace);
        if (!request.tools().isEmpty()) { ArrayNode tools = root.putArray("tools"); for (ToolDefinition tool : request.tools()) { ObjectNode function = tools.addObject().put("type", "function").putObject("function"); function.put("name", tool.name()); function.put("description", tool.description()); function.putPOJO("parameters", tool.inputSchema()); } }
        ArrayNode messages = root.putArray("messages");
        for (ChatMessage message : request.messages()) {
            ObjectNode wireMessage = messages.addObject();
            wireMessage.put("role", message.role().name().toLowerCase(Locale.ROOT));
            wireMessage.put("content", message.content());
            if (message.role() == ChatMessageRole.TOOL) wireMessage.put("tool_call_id", message.toolCallId());
            if (!message.toolCalls().isEmpty()) { ArrayNode calls = wireMessage.putArray("tool_calls"); for (ToolCall call : message.toolCalls()) calls.addObject().put("id", call.id()).put("type", "function").putObject("function").put("name", call.name()).put("arguments", JSON.valueToTree(call.arguments()).toString()); }
        }
        try {
            return JSON.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize OpenRouter request", exception);
        }
    }

    private ChatResponse toChatResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new OpenRouterHttpException(response.statusCode(), response.body());
        try {
            JsonNode root = JSON.readTree(response.body()); JsonNode choice = root.path("choices").path(0); JsonNode message = choice.path("message"); @Nullable String content = message.path("content").textValue(); if (content == null) content = "";
            List<ToolCall> calls = new java.util.ArrayList<>(); for (JsonNode call : message.path("tool_calls")) { String arguments = call.path("function").path("arguments").asText("{}"); calls.add(new ToolCall(call.path("id").asText(), call.path("function").path("name").asText(), JSON.readValue(arguments, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }))); }
            if (content.isEmpty() && calls.isEmpty()) throw new OpenRouterResponseException("OpenRouter returned no assistant text or tool calls");
            List<io.cortavyn.model.api.ChatContent> blocks = new java.util.ArrayList<>(); blocks.add(new io.cortavyn.model.api.TextContent(content)); @Nullable String reasoningText = message.path("reasoning").textValue(); if (reasoningText == null) reasoningText = message.path("reasoning_content").textValue(); if (reasoningText != null) blocks.add(new ReasoningContent(reasoningText));
            JsonNode usage = root.path("usage"); TokenUsage tokens = usage.isMissingNode() ? null : new TokenUsage(usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt(), usage.path("total_tokens").asInt());
            return new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, content, blocks, null, calls), new ChatResponseMetadata(root.path("model").textValue(), response.headers().firstValue("x-request-id").orElse(null), choice.path("finish_reason").textValue(), tokens), Map.of());
        } catch (JsonProcessingException exception) {
            throw new OpenRouterResponseException("OpenRouter returned an invalid JSON response", exception);
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

    public static final class Builder {
        private @Nullable HttpClient httpClient;
        private @Nullable URI baseUrl;
        private @Nullable String apiKey;
        private @Nullable String modelName;
        private @Nullable Duration timeout;
        private @Nullable String siteUrl;
        private @Nullable String appTitle;
        private List<String> appCategories = List.of();
        private @Nullable Double temperature;
        private @Nullable Integer maxTokens;
        private @Nullable Double topP;
        private @Nullable List<String> stopSequences;
        private Map<String, Object> providerPreferences = Map.of();
        private @Nullable String route;
        private Map<String, Object> reasoning = Map.of();
        private List<Map<String, Object>> plugins = List.of();
        private @Nullable String sessionId;
        private Map<String, Object> trace = Map.of();

        private Builder() { }

        public Builder httpClient(HttpClient httpClient) { this.httpClient = Objects.requireNonNull(httpClient); return this; }
        public Builder baseUrl(URI baseUrl) { this.baseUrl = Objects.requireNonNull(baseUrl); return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder modelName(String modelName) { this.modelName = modelName; return this; }
        public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }
        public Builder siteUrl(String siteUrl) { this.siteUrl = requireNonBlank(siteUrl, "siteUrl"); return this; }
        public Builder appTitle(String appTitle) { this.appTitle = requireNonBlank(appTitle, "appTitle"); return this; }
        public Builder appCategories(List<String> appCategories) { this.appCategories = List.copyOf(appCategories); return this; }
        public Builder temperature(double temperature) { this.temperature = temperature; return this; }
        public Builder maxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
        public Builder topP(double topP) { this.topP = topP; return this; }
        public Builder stopSequences(List<String> stopSequences) { this.stopSequences = List.copyOf(stopSequences); return this; }
        public Builder providerPreferences(Map<String, Object> providerPreferences) { this.providerPreferences = Map.copyOf(providerPreferences); return this; }
        public Builder route(String route) { this.route = requireNonBlank(route, "route"); return this; }
        public Builder reasoning(Map<String, Object> reasoning) { this.reasoning = Map.copyOf(reasoning); return this; }
        public Builder plugins(List<Map<String, Object>> plugins) { this.plugins = List.copyOf(plugins); return this; }
        public Builder sessionId(String sessionId) { this.sessionId = requireNonBlank(sessionId, "sessionId"); return this; }
        public Builder trace(Map<String, Object> trace) { this.trace = Map.copyOf(trace); return this; }
        public OpenRouterChatModel build() { return new OpenRouterChatModel(this); }
    }
}
