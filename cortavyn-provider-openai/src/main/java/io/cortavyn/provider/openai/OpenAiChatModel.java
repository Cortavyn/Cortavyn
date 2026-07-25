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
import io.cortavyn.model.api.ChatResponseMetadata;
import io.cortavyn.model.api.TokenUsage;
import io.cortavyn.model.api.ToolCall;
import io.cortavyn.model.api.ToolDefinition;
import io.cortavyn.model.api.StructuredOutputChatModel;
import io.cortavyn.model.api.StructuredOutputSchema;
import io.cortavyn.model.api.StreamingChatModel;
import io.cortavyn.model.api.ChatStreamEvent;
import io.cortavyn.model.api.ChatStreamPublishers;
import io.cortavyn.model.api.OpenAiChatStreamAccumulator;
import io.cortavyn.model.api.ImageContent;
import io.cortavyn.model.api.AudioContent;
import io.cortavyn.model.api.DocumentContent;
import io.cortavyn.model.api.TextContent;
import io.cortavyn.model.api.MediaUris;
import io.cortavyn.model.api.UnsupportedChatContentException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Publisher;
import org.jspecify.annotations.Nullable;

/**
 * An OpenAI Chat Completions API adapter.
 *
 * <p>The adapter depends only on Cortavyn's portable model API. Provider configuration is supplied
 * through {@link Builder}; no credentials are read from the environment automatically.</p>
 */
public final class OpenAiChatModel implements StructuredOutputChatModel, StreamingChatModel {
    private static final URI DEFAULT_BASE_URL = URI.create("https://api.openai.com/v1/");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient httpClient;
    private final URI chatCompletionsUri;
    private final String apiKey;
    private final String modelName;
    private final Duration timeout;
    private final @Nullable Double temperature;
    private final @Nullable Integer maxTokens;
    private final @Nullable Double topP;
    private final @Nullable Double frequencyPenalty;
    private final @Nullable Double presencePenalty;
    private final @Nullable Integer seed;
    private final List<String> stopSequences;
    private final Map<String, Object> additionalParameters;

    private OpenAiChatModel(Builder builder) {
        this.httpClient = builder.httpClient == null ? HttpClient.newHttpClient() : builder.httpClient;
        this.chatCompletionsUri = normalizeBaseUrl(builder.baseUrl).resolve("chat/completions");
        this.apiKey = requireNonBlank(builder.apiKey, "apiKey");
        this.modelName = requireNonBlank(builder.modelName, "modelName");
        this.timeout = builder.timeout == null ? DEFAULT_TIMEOUT : builder.timeout;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.topP = builder.topP;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.presencePenalty = builder.presencePenalty;
        this.seed = builder.seed;
        this.stopSequences = builder.stopSequences == null ? List.of() : List.copyOf(builder.stopSequences);
        this.additionalParameters = Map.copyOf(builder.additionalParameters);
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (temperature != null && (temperature < 0 || temperature > 2)) throw new IllegalArgumentException("temperature must be in [0.0, 2.0]");
        if (maxTokens != null && maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be positive");
        if (topP != null && (topP < 0 || topP > 1)) throw new IllegalArgumentException("topP must be in [0.0, 1.0]");
        if (frequencyPenalty != null && (frequencyPenalty < -2 || frequencyPenalty > 2)) throw new IllegalArgumentException("frequencyPenalty must be in [-2.0, 2.0]");
        if (presencePenalty != null && (presencePenalty < -2 || presencePenalty > 2)) throw new IllegalArgumentException("presencePenalty must be in [-2.0, 2.0]");
        additionalParameters.keySet().forEach(key -> {
            if (RESERVED_PARAMETERS.contains(key)) throw new IllegalArgumentException("additionalParameters must not override " + key);
        });
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public CompletionStage<ChatResponse> complete(ChatRequest request) {
        return completeInternal(request, null);
    }

    @Override public Publisher<ChatStreamEvent> stream(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        var accumulator = new OpenAiChatStreamAccumulator();
        HttpRequest httpRequest = HttpRequest.newBuilder(chatCompletionsUri).timeout(timeout)
                .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                .header("Accept", "text/event-stream").header("User-Agent", "cortavyn-java")
                .POST(HttpRequest.BodyPublishers.ofString(toStreamRequestJson(request))).build();
        return ChatStreamPublishers.fromLines(httpClient, httpRequest,
                response -> new OpenAiHttpException(response.statusCode(), ""), accumulator::accept, accumulator::complete);
    }

    @Override public CompletionStage<ChatResponse> complete(ChatRequest request, StructuredOutputSchema schema) {
        return completeInternal(request, Objects.requireNonNull(schema, "schema must not be null"));
    }
    private CompletionStage<ChatResponse> completeInternal(ChatRequest request, @Nullable StructuredOutputSchema schema) {
        Objects.requireNonNull(request, "request must not be null");
        var httpRequest = HttpRequest.newBuilder(chatCompletionsUri)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("User-Agent", "cortavyn-java")
                .POST(HttpRequest.BodyPublishers.ofString(toRequestJson(request, schema)))
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
            JsonNode message = choice.path("message");
            @Nullable String content = message.path("content").textValue();
            if (content == null) content = "";
            List<ToolCall> toolCalls = new java.util.ArrayList<>();
            for (JsonNode call : message.path("tool_calls")) {
                String id = call.path("id").asText();
                String name = call.path("function").path("name").asText();
                String arguments = call.path("function").path("arguments").asText("{}");
                try { toolCalls.add(new ToolCall(id, name, JSON.readValue(arguments, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }))); }
                catch (JsonProcessingException exception) { throw new OpenAiResponseException("OpenAI returned invalid tool-call arguments", exception); }
            }
            if (content.isEmpty() && toolCalls.isEmpty()) throw new OpenAiResponseException("OpenAI returned neither assistant content nor tool calls");
            JsonNode usage = JSON.readTree(response.body()).path("usage");
            TokenUsage tokenUsage = usage.isMissingNode() ? null : new TokenUsage(usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt(), usage.path("total_tokens").asInt());
            ChatResponseMetadata metadata = new ChatResponseMetadata(JSON.readTree(response.body()).path("model").textValue(), response.headers().firstValue("x-request-id").orElse(null), choice.path("finish_reason").textValue(), tokenUsage);
            List<io.cortavyn.model.api.ChatContent> blocks = new java.util.ArrayList<>(); blocks.add(new io.cortavyn.model.api.TextContent(content)); @Nullable String reasoning = message.path("reasoning_content").textValue(); if (reasoning != null) blocks.add(new io.cortavyn.model.api.ReasoningContent(reasoning));
            return new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, content, blocks, null, toolCalls), metadata, Map.of());
        } catch (JsonProcessingException exception) {
            throw new OpenAiResponseException("OpenAI returned an invalid JSON response", exception);
        }
    }

    String toRequestJson(ChatRequest request) {
        return toRequestJson(request, null);
    }
    String toStreamRequestJson(ChatRequest request) {
        try { ObjectNode root = (ObjectNode) JSON.readTree(toRequestJson(request)); root.put("stream", true); root.putObject("stream_options").put("include_usage", true); return JSON.writeValueAsString(root); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize OpenAI streaming request", exception); }
    }
    String toRequestJson(ChatRequest request, @Nullable StructuredOutputSchema schema) {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", modelName);
        if (temperature != null) root.put("temperature", temperature);
        if (maxTokens != null) root.put("max_tokens", maxTokens);
        if (topP != null) root.put("top_p", topP);
        if (frequencyPenalty != null) root.put("frequency_penalty", frequencyPenalty);
        if (presencePenalty != null) root.put("presence_penalty", presencePenalty);
        if (seed != null) root.put("seed", seed);
        if (!stopSequences.isEmpty()) root.putPOJO("stop", stopSequences);
        if (!request.tools().isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (ToolDefinition tool : request.tools()) {
                ObjectNode function = tools.addObject().put("type", "function").putObject("function");
                function.put("name", tool.name());
                function.put("description", tool.description());
                function.putPOJO("parameters", tool.inputSchema());
            }
        }
        if (schema != null) root.putObject("response_format").put("type", "json_schema").putObject("json_schema")
                .put("name", schema.name()).put("strict", schema.strict()).putPOJO("schema", schema.jsonSchema());
        additionalParameters.forEach(root::putPOJO);
        ArrayNode messages = root.putArray("messages");
        for (ChatMessage message : request.messages()) {
            ObjectNode wireMessage = messages.addObject();
            wireMessage.put("role", message.role().name().toLowerCase(Locale.ROOT));
            addContent(wireMessage, message);
            if (message.role() == ChatMessageRole.TOOL) wireMessage.put("tool_call_id", message.toolCallId());
            addToolCalls(wireMessage, message.toolCalls());
        }
        try {
            return JSON.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize OpenAI request", exception);
        }
    }

    private static void addContent(ObjectNode message, ChatMessage source) {
        if (source.contentBlocks().size() == 1 && source.contentBlocks().getFirst() instanceof TextContent text) { message.put("content", text.text()); return; }
        ArrayNode content = message.putArray("content");
        for (io.cortavyn.model.api.ChatContent block : source.contentBlocks()) {
            if (block instanceof TextContent text) content.addObject().put("type", "text").put("text", text.text());
            else if (block instanceof ImageContent image) content.addObject().put("type", "image_url").putObject("image_url").put("url", image.uri().toString());
            else if (block instanceof AudioContent audio) content.addObject().put("type", "input_audio").putObject("input_audio").put("data", MediaUris.base64Data(audio.uri(), "OpenAI Chat Completions", audio)).put("format", audio.mediaType().substring(audio.mediaType().indexOf('/') + 1));
            else if (block instanceof DocumentContent document) throw new UnsupportedChatContentException("OpenAI Chat Completions", document);
            else if (!(block instanceof io.cortavyn.model.api.ReasoningContent)) throw new UnsupportedChatContentException("OpenAI Chat Completions", block);
        }
    }

    private static void addToolCalls(ObjectNode message, List<ToolCall> toolCalls) {
        if (toolCalls.isEmpty()) return;
        ArrayNode calls = message.putArray("tool_calls");
        for (ToolCall call : toolCalls) {
            calls.addObject().put("id", call.id()).put("type", "function")
                    .putObject("function").put("name", call.name()).put("arguments", toJson(call.arguments()));
        }
    }

    private static String toJson(Map<String, Object> value) {
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize tool-call arguments", exception); }
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

    private static final Set<String> RESERVED_PARAMETERS = Set.of(
            "model", "messages", "temperature", "max_tokens", "top_p", "frequency_penalty", "presence_penalty", "seed", "stop");

    public static final class Builder {
        private @Nullable HttpClient httpClient;
        private @Nullable URI baseUrl;
        private @Nullable String apiKey;
        private @Nullable String modelName;
        private @Nullable Duration timeout;
        private @Nullable Double temperature;
        private @Nullable Integer maxTokens;
        private @Nullable Double topP;
        private @Nullable Double frequencyPenalty;
        private @Nullable Double presencePenalty;
        private @Nullable Integer seed;
        private @Nullable List<String> stopSequences;
        private Map<String, Object> additionalParameters = Map.of();

        private Builder() {
        }

        /** @param httpClient HTTP client used for requests; defaults to a new JDK client */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
            return this;
        }

        /** @param baseUrl API base URI, useful for an OpenAI-compatible proxy; defaults to OpenAI's v1 endpoint */
        public Builder baseUrl(URI baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
            return this;
        }

        /** @param apiKey OpenAI API key sent as a Bearer credential; required */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** @param modelName Chat Completions model identifier; defaults to the adapter's documented default */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /** @param timeout per-request deadline; defaults to two minutes */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /** @param temperature sampling randomness in OpenAI's accepted range */
        public Builder temperature(double temperature) { this.temperature = temperature; return this; }
        /** @param maxTokens maximum generated tokens for this completion */
        public Builder maxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
        /** @param topP nucleus-sampling probability; use instead of temperature when appropriate */
        public Builder topP(double topP) { this.topP = topP; return this; }
        /** @param frequencyPenalty penalty for repeatedly used tokens */
        public Builder frequencyPenalty(double frequencyPenalty) { this.frequencyPenalty = frequencyPenalty; return this; }
        /** @param presencePenalty penalty that encourages introducing new topics */
        public Builder presencePenalty(double presencePenalty) { this.presencePenalty = presencePenalty; return this; }
        /** @param seed best-effort deterministic sampling seed */
        public Builder seed(int seed) { this.seed = seed; return this; }
        /** @param stopSequences sequences that terminate generation early */
        public Builder stopSequences(List<String> stopSequences) { this.stopSequences = List.copyOf(stopSequences); return this; }
        /** @param additionalParameters provider fields not represented by the portable API; reserved fields are rejected */
        public Builder additionalParameters(Map<String, Object> additionalParameters) { this.additionalParameters = Map.copyOf(additionalParameters); return this; }

        /** @return an immutable, thread-safe OpenAI Chat Completions adapter */
        public OpenAiChatModel build() {
            return new OpenAiChatModel(this);
        }
    }
}
