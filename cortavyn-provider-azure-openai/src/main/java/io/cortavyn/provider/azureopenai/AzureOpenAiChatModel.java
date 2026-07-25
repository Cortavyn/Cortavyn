package io.cortavyn.provider.azureopenai;

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
import io.cortavyn.model.api.TextContent;
import io.cortavyn.model.api.ImageContent;
import io.cortavyn.model.api.AudioContent;
import io.cortavyn.model.api.DocumentContent;
import io.cortavyn.model.api.MediaUris;
import io.cortavyn.model.api.UnsupportedChatContentException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Publisher;
import org.jspecify.annotations.Nullable;

/**
 * An Azure OpenAI Chat Completions adapter.
 *
 * <p>Azure routes chat requests through a deployment. Accordingly, {@code deploymentName}, not a
 * model name, is required. This mirrors LangChain's {@code AzureChatOpenAI} distinction.</p>
 */
public final class AzureOpenAiChatModel implements StructuredOutputChatModel, StreamingChatModel {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final Set<String> RESERVED_PARAMETERS = Set.of(
            "messages", "temperature", "max_completion_tokens", "top_p", "frequency_penalty",
            "presence_penalty", "seed", "stop", "reasoning_effort");

    private final HttpClient httpClient;
    private final URI chatCompletionsUri;
    private final String apiKey;
    private final Duration timeout;
    private final @Nullable Double temperature;
    private final @Nullable Integer maxTokens;
    private final @Nullable Double topP;
    private final @Nullable Double frequencyPenalty;
    private final @Nullable Double presencePenalty;
    private final @Nullable Integer seed;
    private final List<String> stopSequences;
    private final @Nullable String reasoningEffort;
    private final Map<String, Object> additionalParameters;
    private final boolean supportsImages;
    private final boolean supportsAudio;

    private AzureOpenAiChatModel(Builder builder) {
        httpClient = builder.httpClient == null ? HttpClient.newHttpClient() : builder.httpClient;
        apiKey = requireNonBlank(builder.apiKey, "apiKey");
        String endpoint = normalizeEndpoint(builder.endpoint);
        String deployment = requireNonBlank(builder.deploymentName, "deploymentName");
        String apiVersion = requireNonBlank(builder.apiVersion, "apiVersion");
        chatCompletionsUri = URI.create(endpoint + "openai/deployments/" + deployment
                + "/chat/completions?api-version=" + apiVersion);
        timeout = builder.timeout == null ? DEFAULT_TIMEOUT : builder.timeout;
        temperature = builder.temperature;
        maxTokens = builder.maxTokens;
        topP = builder.topP;
        frequencyPenalty = builder.frequencyPenalty;
        presencePenalty = builder.presencePenalty;
        seed = builder.seed;
        stopSequences = builder.stopSequences == null ? List.of() : List.copyOf(builder.stopSequences);
        reasoningEffort = builder.reasoningEffort;
        additionalParameters = Map.copyOf(builder.additionalParameters);
        supportsImages = builder.supportsImages;
        supportsAudio = builder.supportsAudio;
        validate();
    }

    public static Builder builder() { return new Builder(); }

    @Override
    public CompletionStage<ChatResponse> complete(ChatRequest request) {
        return completeInternal(request, null);
    }
    @Override public Publisher<ChatStreamEvent> stream(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null"); var accumulator = new OpenAiChatStreamAccumulator();
        HttpRequest httpRequest = HttpRequest.newBuilder(chatCompletionsUri).timeout(timeout).header("api-key", apiKey)
                .header("Content-Type", "application/json").header("Accept", "text/event-stream").header("User-Agent", "cortavyn-java")
                .POST(HttpRequest.BodyPublishers.ofString(toStreamRequestJson(request))).build();
        return ChatStreamPublishers.fromLines(httpClient, httpRequest, response -> new AzureOpenAiHttpException(response.statusCode(), ""), accumulator::accept, accumulator::complete);
    }
    @Override public CompletionStage<ChatResponse> complete(ChatRequest request, StructuredOutputSchema schema) {
        return completeInternal(request, Objects.requireNonNull(schema, "schema must not be null"));
    }
    private CompletionStage<ChatResponse> completeInternal(ChatRequest request, @Nullable StructuredOutputSchema schema) {
        Objects.requireNonNull(request, "request must not be null");
        HttpRequest httpRequest = HttpRequest.newBuilder(chatCompletionsUri)
                .timeout(timeout)
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("User-Agent", "cortavyn-java")
                .POST(HttpRequest.BodyPublishers.ofString(toRequestJson(request, schema)))
                .build();
        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::toChatResponse);
    }

    String toRequestJson(ChatRequest request) {
        return toRequestJson(request, null);
    }
    String toStreamRequestJson(ChatRequest request) { try { ObjectNode root = (ObjectNode) JSON.readTree(toRequestJson(request)); root.put("stream", true); return JSON.writeValueAsString(root); } catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize Azure OpenAI streaming request", exception); } }
    String toRequestJson(ChatRequest request, @Nullable StructuredOutputSchema schema) {
        ObjectNode root = JSON.createObjectNode();
        if (temperature != null) root.put("temperature", temperature);
        if (maxTokens != null) root.put("max_completion_tokens", maxTokens);
        if (topP != null) root.put("top_p", topP);
        if (frequencyPenalty != null) root.put("frequency_penalty", frequencyPenalty);
        if (presencePenalty != null) root.put("presence_penalty", presencePenalty);
        if (seed != null) root.put("seed", seed);
        if (!stopSequences.isEmpty()) root.putPOJO("stop", stopSequences);
        if (reasoningEffort != null) root.put("reasoning_effort", reasoningEffort);
        if (!request.tools().isEmpty()) { ArrayNode tools = root.putArray("tools"); for (ToolDefinition tool : request.tools()) { ObjectNode function = tools.addObject().put("type", "function").putObject("function"); function.put("name", tool.name()); function.put("description", tool.description()); function.putPOJO("parameters", tool.inputSchema()); } }
        if (schema != null) root.putObject("response_format").put("type", "json_schema").putObject("json_schema").put("name", schema.name()).put("strict", schema.strict()).putPOJO("schema", schema.jsonSchema());
        additionalParameters.forEach(root::putPOJO);
        ArrayNode messages = root.putArray("messages");
        for (ChatMessage message : request.messages()) {
            ObjectNode wireMessage = messages.addObject();
            wireMessage.put("role", message.role().name().toLowerCase(Locale.ROOT));
            addContent(wireMessage, message);
            if (message.role() == ChatMessageRole.TOOL) wireMessage.put("tool_call_id", message.toolCallId());
            if (!message.toolCalls().isEmpty()) { ArrayNode calls = wireMessage.putArray("tool_calls"); for (ToolCall call : message.toolCalls()) calls.addObject().put("id", call.id()).put("type", "function").putObject("function").put("name", call.name()).put("arguments", JSON.valueToTree(call.arguments()).toString()); }
        }
        try { return JSON.writeValueAsString(root); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize Azure OpenAI request", exception); }
    }

    private void addContent(ObjectNode message, ChatMessage source) {
        if (source.contentBlocks().size() == 1 && source.contentBlocks().getFirst() instanceof TextContent text) { message.put("content", text.text()); return; }
        ArrayNode content = message.putArray("content");
        for (io.cortavyn.model.api.ChatContent block : source.contentBlocks()) {
            if (block instanceof TextContent text) content.addObject().put("type", "text").put("text", text.text());
            else if (block instanceof ImageContent image) {
                if (!supportsImages) throw new UnsupportedChatContentException("Azure OpenAI deployment", image);
                content.addObject().put("type", "image_url").putObject("image_url").put("url", image.uri().toString());
            } else if (block instanceof AudioContent audio) {
                if (!supportsAudio) throw new UnsupportedChatContentException("Azure OpenAI deployment", audio);
                content.addObject().put("type", "input_audio").putObject("input_audio").put("data", MediaUris.base64Data(audio.uri(), "Azure OpenAI deployment", audio)).put("format", mediaSubtype(audio.mediaType(), audio));
            } else if (block instanceof DocumentContent document) throw new UnsupportedChatContentException("Azure OpenAI Chat Completions", document);
            else if (!(block instanceof io.cortavyn.model.api.ReasoningContent)) throw new UnsupportedChatContentException("Azure OpenAI deployment", block);
        }
    }
    private static String mediaSubtype(String mediaType, io.cortavyn.model.api.ChatContent content) {
        int slash = mediaType.indexOf('/');
        if (slash < 1 || slash == mediaType.length() - 1) throw new UnsupportedChatContentException("Azure OpenAI deployment", content.getClass().getSimpleName() + " media type");
        return mediaType.substring(slash + 1);
    }

    private ChatResponse toChatResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AzureOpenAiHttpException(response.statusCode(), response.body());
        }
        try {
            JsonNode root = JSON.readTree(response.body()); JsonNode choice = root.path("choices").path(0); JsonNode message = choice.path("message"); @Nullable String content = message.path("content").textValue(); if (content == null) content = "";
            List<ToolCall> calls = new java.util.ArrayList<>(); for (JsonNode call : message.path("tool_calls")) { String arguments = call.path("function").path("arguments").asText("{}"); calls.add(new ToolCall(call.path("id").asText(), call.path("function").path("name").asText(), JSON.readValue(arguments, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }))); }
            if (content.isEmpty() && calls.isEmpty()) throw new AzureOpenAiResponseException("Azure OpenAI returned no assistant text or tool calls");
            JsonNode usage = root.path("usage"); TokenUsage tokens = usage.isMissingNode() ? null : new TokenUsage(usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt(), usage.path("total_tokens").asInt());
            List<io.cortavyn.model.api.ChatContent> blocks = new java.util.ArrayList<>(); blocks.add(new io.cortavyn.model.api.TextContent(content)); @Nullable String reasoning = message.path("reasoning_content").textValue(); if (reasoning != null) blocks.add(new io.cortavyn.model.api.ReasoningContent(reasoning));
            return new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, content, blocks, null, calls), new ChatResponseMetadata(null, response.headers().firstValue("x-request-id").orElse(null), choice.path("finish_reason").textValue(), tokens), Map.of());
        } catch (JsonProcessingException exception) {
            throw new AzureOpenAiResponseException("Azure OpenAI returned an invalid JSON response", exception);
        }
    }

    private void validate() {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        if (temperature != null && (temperature < 0 || temperature > 2)) throw new IllegalArgumentException("temperature must be in [0.0, 2.0]");
        if (maxTokens != null && maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be positive");
        if (topP != null && (topP < 0 || topP > 1)) throw new IllegalArgumentException("topP must be in [0.0, 1.0]");
        if (frequencyPenalty != null && (frequencyPenalty < -2 || frequencyPenalty > 2)) throw new IllegalArgumentException("frequencyPenalty must be in [-2.0, 2.0]");
        if (presencePenalty != null && (presencePenalty < -2 || presencePenalty > 2)) throw new IllegalArgumentException("presencePenalty must be in [-2.0, 2.0]");
        additionalParameters.keySet().forEach(key -> {
            if (RESERVED_PARAMETERS.contains(key)) throw new IllegalArgumentException("additionalParameters must not override " + key);
        });
    }

    private static String normalizeEndpoint(@Nullable URI endpoint) {
        if (endpoint == null) throw new IllegalArgumentException("endpoint must not be null");
        String value = endpoint.toString();
        if (value.isBlank()) throw new IllegalArgumentException("endpoint must not be blank");
        return value.endsWith("/") ? value : value + "/";
    }

    private static String requireNonBlank(@Nullable String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    public static final class Builder {
        private @Nullable HttpClient httpClient;
        private @Nullable URI endpoint;
        private @Nullable String apiKey;
        private @Nullable String deploymentName;
        private @Nullable String apiVersion;
        private @Nullable Duration timeout;
        private @Nullable Double temperature;
        private @Nullable Integer maxTokens;
        private @Nullable Double topP;
        private @Nullable Double frequencyPenalty;
        private @Nullable Double presencePenalty;
        private @Nullable Integer seed;
        private @Nullable List<String> stopSequences;
        private @Nullable String reasoningEffort;
        private Map<String, Object> additionalParameters = Map.of();
        private boolean supportsImages;
        private boolean supportsAudio;
        private Builder() { }
        /** @param value HTTP client used for requests */
        public Builder httpClient(HttpClient value) { httpClient = Objects.requireNonNull(value); return this; }
        /** @param value Azure resource endpoint, without a deployment path */
        public Builder endpoint(URI value) { endpoint = Objects.requireNonNull(value); return this; }
        /** @param value Azure OpenAI API key; required */
        public Builder apiKey(String value) { apiKey = value; return this; }
        /** @param value Azure deployment name, which selects the deployed model; required */
        public Builder deploymentName(String value) { deploymentName = value; return this; }
        /** @param value Azure REST API version used as a query parameter; required */
        public Builder apiVersion(String value) { apiVersion = value; return this; }
        /** @param value per-request deadline */
        public Builder timeout(Duration value) { timeout = value; return this; }
        public Builder temperature(double value) { temperature = value; return this; }
        public Builder maxTokens(int value) { maxTokens = value; return this; }
        public Builder topP(double value) { topP = value; return this; }
        public Builder frequencyPenalty(double value) { frequencyPenalty = value; return this; }
        public Builder presencePenalty(double value) { presencePenalty = value; return this; }
        public Builder seed(int value) { seed = value; return this; }
        public Builder stopSequences(List<String> value) { stopSequences = List.copyOf(value); return this; }
        /** Sets the reasoning effort for Azure OpenAI reasoning deployments. */
        public Builder reasoningEffort(String value) { reasoningEffort = requireNonBlank(value, "reasoningEffort"); return this; }
        public Builder additionalParameters(Map<String, Object> value) { additionalParameters = Map.copyOf(value); return this; }
        /** Enables image input for a vision-capable Azure deployment. */
        public Builder supportsImages(boolean value) { supportsImages = value; return this; }
        /** Enables audio input for an audio-capable Azure deployment. */
        public Builder supportsAudio(boolean value) { supportsAudio = value; return this; }
        /** @return an immutable Azure OpenAI Chat Completions adapter */
        public AzureOpenAiChatModel build() { return new AzureOpenAiChatModel(this); }
    }
}
