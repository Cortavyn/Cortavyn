package io.cortavyn.provider.gemini;

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
import io.cortavyn.model.api.StructuredOutputChatModel;
import io.cortavyn.model.api.StructuredOutputSchema;
import io.cortavyn.model.api.StreamingChatModel;
import io.cortavyn.model.api.ChatStreamEvent;
import io.cortavyn.model.api.ChatStreamPublishers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Publisher;
import org.jspecify.annotations.Nullable;

/** A Gemini Developer API adapter following LangChain's system-instruction and role mapping. */
public final class GeminiChatModel implements StructuredOutputChatModel, StreamingChatModel {
    private static final URI DEFAULT_BASE_URL = URI.create("https://generativelanguage.googleapis.com/v1beta/");
    private static final String DEFAULT_MODEL_NAME = "gemini-2.5-flash";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient httpClient;
    private final URI generateContentUri;
    private final String apiKey;
    private final String modelName;
    private final Duration timeout;
    private final @Nullable Double temperature;
    private final @Nullable Double topP;
    private final @Nullable Integer topK;
    private final @Nullable Integer maxOutputTokens;
    private final List<String> stopSequences;
    private final Map<String, Object> thinkingConfig;

    private GeminiChatModel(Builder builder) {
        this.httpClient = builder.httpClient == null ? HttpClient.newHttpClient() : builder.httpClient;
        this.apiKey = requireNonBlank(builder.apiKey, "apiKey");
        this.modelName = builder.modelName == null ? DEFAULT_MODEL_NAME : requireNonBlank(builder.modelName, "modelName");
        this.generateContentUri = normalizeBaseUrl(builder.baseUrl).resolve("models/" + modelName + ":generateContent");
        this.timeout = builder.timeout == null ? DEFAULT_TIMEOUT : builder.timeout;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.topK = builder.topK;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.stopSequences = builder.stopSequences == null ? List.of() : List.copyOf(builder.stopSequences);
        this.thinkingConfig = Map.copyOf(builder.thinkingConfig);
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        if (temperature != null && (temperature < 0 || temperature > 2)) throw new IllegalArgumentException("temperature must be in [0.0, 2.0]");
        if (topP != null && (topP < 0 || topP > 1)) throw new IllegalArgumentException("topP must be in [0.0, 1.0]");
        if (topK != null && topK <= 0) throw new IllegalArgumentException("topK must be positive");
        if (maxOutputTokens != null && maxOutputTokens <= 0) throw new IllegalArgumentException("maxOutputTokens must be positive");
    }

    public static Builder builder() { return new Builder(); }

    @Override
    public CompletionStage<ChatResponse> complete(ChatRequest request) {
        return completeInternal(request, null);
    }
    @Override public Publisher<ChatStreamEvent> stream(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null"); var accumulator = new GeminiStreamAccumulator(modelName);
        URI streamUri = URI.create(generateContentUri.toString().replace(":generateContent", ":streamGenerateContent") + "?alt=sse");
        HttpRequest httpRequest = HttpRequest.newBuilder(streamUri).timeout(timeout).header("x-goog-api-key", apiKey).header("Content-Type", "application/json").header("Accept", "text/event-stream").header("User-Agent", "cortavyn-java").POST(HttpRequest.BodyPublishers.ofString(toRequestJson(request))).build();
        return ChatStreamPublishers.fromLines(httpClient, httpRequest, response -> new GeminiHttpException(response.statusCode(), ""), accumulator::accept, accumulator::complete);
    }
    @Override public CompletionStage<ChatResponse> complete(ChatRequest request, StructuredOutputSchema schema) {
        return completeInternal(request, Objects.requireNonNull(schema, "schema must not be null"));
    }
    private CompletionStage<ChatResponse> completeInternal(ChatRequest request, @Nullable StructuredOutputSchema schema) {
        Objects.requireNonNull(request, "request must not be null");
        HttpRequest httpRequest = HttpRequest.newBuilder(generateContentUri)
                .timeout(timeout)
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "cortavyn-java")
                .POST(HttpRequest.BodyPublishers.ofString(toRequestJson(request, schema)))
                .build();
        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).thenApply(this::toChatResponse);
    }

    String toRequestJson(ChatRequest request) {
        return toRequestJson(request, null);
    }
    String toRequestJson(ChatRequest request, @Nullable StructuredOutputSchema schema) {
        ObjectNode root = JSON.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        ArrayNode systemParts = null;
        for (ChatMessage message : request.messages()) {
            if (message.role() == ChatMessageRole.SYSTEM) {
                if (systemParts == null) systemParts = root.putObject("systemInstruction").putArray("parts");
                systemParts.addObject().put("text", message.content());
                continue;
            }
            ObjectNode content = contents.addObject();
            content.put("role", message.role() == ChatMessageRole.ASSISTANT ? "model" : "user");
            if (message.role() == ChatMessageRole.TOOL) {
                String toolCallId = Objects.requireNonNull(message.toolCallId(), "TOOL messages require toolCallId");
                ObjectNode functionResponse = content.putArray("parts").addObject().putObject("functionResponse");
                functionResponse.put("name", toolNameFor(request.messages(), toolCallId));
                functionResponse.put("id", toolCallId);
                functionResponse.putObject("response").put("content", message.content());
            } else if (message.role() == ChatMessageRole.ASSISTANT) {
                ArrayNode parts = content.putArray("parts");
                for (io.cortavyn.model.api.ChatContent block : message.contentBlocks()) {
                    if (block instanceof io.cortavyn.model.api.ReasoningContent reasoning) { ObjectNode thought = parts.addObject().put("text", reasoning.text()).put("thought", true); Object signature = reasoning.providerState().get("thoughtSignature"); if (signature instanceof String value && !value.isBlank()) thought.put("thoughtSignature", value); }
                    else if (block instanceof io.cortavyn.model.api.TextContent text) parts.addObject().put("text", text.text());
                }
                for (ToolCall toolCall : message.toolCalls()) {
                    ObjectNode part = parts.addObject();
                    part.putObject("functionCall").put("name", toolCall.name()).put("id", toolCall.id()).putPOJO("args", toolCall.arguments());
                    Object signature = toolCall.metadata().get("gemini.thoughtSignature");
                    if (signature instanceof String value && !value.isBlank()) part.put("thoughtSignature", value);
                }
                if (parts.isEmpty()) parts.addObject().put("text", message.content());
            } else content.putArray("parts").addObject().put("text", message.content());
        }
        if (contents.isEmpty()) throw new IllegalArgumentException("Gemini requires at least one non-system message");
        ObjectNode generationConfig = root.putObject("generationConfig");
        if (temperature != null) generationConfig.put("temperature", temperature);
        if (topP != null) generationConfig.put("topP", topP);
        if (topK != null) generationConfig.put("topK", topK);
        if (maxOutputTokens != null) generationConfig.put("maxOutputTokens", maxOutputTokens);
        if (!stopSequences.isEmpty()) generationConfig.putPOJO("stopSequences", stopSequences);
        if (!thinkingConfig.isEmpty()) generationConfig.putPOJO("thinkingConfig", thinkingConfig);
        if (schema != null) { generationConfig.put("responseMimeType", "application/json"); generationConfig.putPOJO("responseJsonSchema", schema.jsonSchema()); }
        if (generationConfig.isEmpty()) root.remove("generationConfig");
        if (!request.tools().isEmpty()) {
            ArrayNode declarations = root.putArray("tools").addObject().putArray("functionDeclarations");
            for (ToolDefinition tool : request.tools()) { ObjectNode declaration = declarations.addObject(); declaration.put("name", tool.name()); declaration.put("description", tool.description()); declaration.putPOJO("parameters", tool.inputSchema()); }
        }
        try {
            return JSON.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Gemini request", exception);
        }
    }

    private ChatResponse toChatResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new GeminiHttpException(response.statusCode(), response.body());
        try {
            JsonNode root = JSON.readTree(response.body()); JsonNode candidate = root.path("candidates").path(0); JsonNode parts = candidate.path("content").path("parts");
            StringBuilder text = new StringBuilder();
            List<ToolCall> toolCalls = new java.util.ArrayList<>();
            List<io.cortavyn.model.api.ChatContent> blocks = new java.util.ArrayList<>();
            for (JsonNode part : parts) {
                @Nullable String value = part.path("text").textValue();
                if (value != null) text.append(value);
                if (part.path("thought").asBoolean(false) && value != null) blocks.add(new ReasoningContent(value, java.util.Map.of("thoughtSignature", part.path("thoughtSignature").asText())));
                JsonNode call = part.path("functionCall");
                if (!call.isMissingNode()) {
                    String signature = part.path("thoughtSignature").asText();
                    toolCalls.add(new ToolCall(
                            call.path("id").asText(call.path("name").asText()),
                            call.path("name").asText(),
                            JSON.convertValue(call.path("args"), new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() { }),
                            signature.isBlank() ? java.util.Map.of() : java.util.Map.of("gemini.thoughtSignature", signature)));
                }
            }
            if (text.isEmpty() && toolCalls.isEmpty()) throw new GeminiResponseException("Gemini returned neither assistant text nor function calls");
            JsonNode usage = root.path("usageMetadata"); TokenUsage tokens = usage.isMissingNode() ? null : new TokenUsage(usage.path("promptTokenCount").asInt(), usage.path("candidatesTokenCount").asInt(), usage.path("totalTokenCount").asInt());
            blocks.addFirst(new io.cortavyn.model.api.TextContent(text.toString()));
            return new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, text.toString(), blocks, null, toolCalls), new ChatResponseMetadata(modelName, response.headers().firstValue("x-request-id").orElse(null), candidate.path("finishReason").textValue(), tokens), java.util.Map.of());
        } catch (JsonProcessingException exception) {
            throw new GeminiResponseException("Gemini returned an invalid JSON response", exception);
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

    private static String toolNameFor(List<ChatMessage> messages, String toolCallId) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            for (ToolCall toolCall : messages.get(index).toolCalls()) {
                if (toolCall.id().equals(toolCallId)) return toolCall.name();
            }
        }
        return toolCallId;
    }

    public static final class Builder {
        private @Nullable HttpClient httpClient;
        private @Nullable URI baseUrl;
        private @Nullable String apiKey;
        private @Nullable String modelName;
        private @Nullable Duration timeout;
        private @Nullable Double temperature;
        private @Nullable Double topP;
        private @Nullable Integer topK;
        private @Nullable Integer maxOutputTokens;
        private @Nullable List<String> stopSequences;
        private Map<String, Object> thinkingConfig = Map.of();

        private Builder() { }

        public Builder httpClient(HttpClient httpClient) { this.httpClient = Objects.requireNonNull(httpClient); return this; }
        public Builder baseUrl(URI baseUrl) { this.baseUrl = Objects.requireNonNull(baseUrl); return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder modelName(String modelName) { this.modelName = modelName; return this; }
        public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }
        public Builder temperature(double temperature) { this.temperature = temperature; return this; }
        public Builder topP(double topP) { this.topP = topP; return this; }
        public Builder topK(int topK) { this.topK = topK; return this; }
        public Builder maxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; return this; }
        public Builder stopSequences(List<String> stopSequences) { this.stopSequences = List.copyOf(stopSequences); return this; }
        /** Configures Gemini thinking, for example {@code Map.of("includeThoughts", true)}. */
        public Builder thinkingConfig(Map<String, Object> thinkingConfig) { this.thinkingConfig = Map.copyOf(thinkingConfig); return this; }
        public GeminiChatModel build() { return new GeminiChatModel(this); }
    }
}
