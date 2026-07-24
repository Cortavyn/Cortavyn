package io.cortavyn.provider.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cortavyn.model.api.ChatContent;
import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.model.api.ChatResponse;
import io.cortavyn.model.api.ChatResponseMetadata;
import io.cortavyn.model.api.ReasoningContent;
import io.cortavyn.model.api.TextContent;
import io.cortavyn.model.api.TokenUsage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/** OpenAI Responses API adapter with automatic continuation of reasoning conversations. */
public final class OpenAiResponsesChatModel implements ChatModel {
    private static final URI RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient httpClient;
    private final String apiKey;
    private final String modelName;
    private final Duration timeout;

    private OpenAiResponsesChatModel(Builder builder) {
        httpClient = builder.httpClient == null ? HttpClient.newHttpClient() : builder.httpClient;
        apiKey = required(builder.apiKey, "apiKey");
        modelName = required(builder.modelName, "modelName");
        timeout = builder.timeout == null ? Duration.ofMinutes(1) : builder.timeout;
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public CompletionStage<ChatResponse> complete(ChatRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder(RESPONSES_URI)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toRequestJson(request)))
                .build();
        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).thenApply(this::toResponse);
    }

    String toRequestJson(ChatRequest request) {
        ObjectNode root = JSON.createObjectNode().put("model", modelName);
        root.putObject("reasoning")
                .put("effort", request.extensions().getOrDefault("openai.reasoning_effort", "medium").toString());
        root.putArray("include").add("reasoning.encrypted_content");

        Continuation continuation = continuationFor(request);
        if (continuation.responseId() != null) {
            root.put("previous_response_id", continuation.responseId());
        }

        ArrayNode input = root.putArray("input");
        for (ChatMessage message : request.messages().subList(continuation.firstMessageIndex(), request.messages().size())) {
            addMessage(input, message);
        }
        try {
            return JSON.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Responses request", exception);
        }
    }

    private static Continuation continuationFor(ChatRequest request) {
        Object explicitResponseId = request.extensions().get("openai.previous_response_id");
        if (explicitResponseId instanceof String responseId && !responseId.isBlank()) {
            return new Continuation(responseId, 0);
        }
        List<ChatMessage> messages = request.messages();
        for (int index = messages.size() - 1; index >= 0; index--) {
            Object candidate = messages.get(index).metadata().get("openai.response_id");
            if (candidate instanceof String responseId && !responseId.isBlank()) {
                return new Continuation(responseId, index + 1);
            }
        }
        return new Continuation(null, 0);
    }

    private static void addMessage(ArrayNode input, ChatMessage message) {
        for (ChatContent block : message.contentBlocks()) {
            if (block instanceof ReasoningContent reasoning
                    && reasoning.providerState().get("encrypted_content") instanceof String encryptedContent) {
                input.addObject().put("type", "reasoning").put("encrypted_content", encryptedContent);
            }
        }
        if (message.role() == ChatMessageRole.TOOL) {
            input.addObject()
                    .put("type", "function_call_output")
                    .put("call_id", message.toolCallId())
                    .put("output", message.content());
            return;
        }
        input.addObject()
                .put("role", message.role().name().toLowerCase(Locale.ROOT))
                .put("content", message.content());
    }

    private ChatResponse toResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new OpenAiHttpException(response.statusCode(), response.body());
        }
        try {
            JsonNode root = JSON.readTree(response.body());
            StringBuilder text = new StringBuilder();
            List<ChatContent> blocks = new ArrayList<>();
            for (JsonNode item : root.path("output")) {
                if ("message".equals(item.path("type").asText())) {
                    appendText(item, text);
                } else if ("reasoning".equals(item.path("type").asText())) {
                    addReasoning(item, blocks);
                }
            }
            if (text.isEmpty()) {
                throw new OpenAiResponseException("Responses API returned no output text");
            }
            blocks.addFirst(new TextContent(text.toString()));

            JsonNode usage = root.path("usage");
            TokenUsage tokens = usage.isMissingNode()
                    ? null
                    : new TokenUsage(usage.path("input_tokens").asInt(), usage.path("output_tokens").asInt(), usage.path("total_tokens").asInt());
            String responseId = root.path("id").asText();
            Map<String, Object> messageMetadata = responseId.isBlank() ? Map.of() : Map.of("openai.response_id", responseId);
            return new ChatResponse(
                    new ChatMessage(ChatMessageRole.ASSISTANT, text.toString(), blocks, null, List.of(), messageMetadata),
                    new ChatResponseMetadata(root.path("model").textValue(), responseId, root.path("status").textValue(), tokens),
                    responseId.isBlank() ? Map.of() : Map.of("openai.response_id", responseId));
        } catch (JsonProcessingException exception) {
            throw new OpenAiResponseException("Responses API returned invalid JSON", exception);
        }
    }

    private static void appendText(JsonNode message, StringBuilder text) {
        for (JsonNode part : message.path("content")) {
            if ("output_text".equals(part.path("type").asText())) {
                text.append(part.path("text").asText());
            }
        }
    }

    private static void addReasoning(JsonNode item, List<ChatContent> blocks) {
        String summary = item.path("summary").path(0).path("text").asText("");
        String encryptedContent = item.path("encrypted_content").asText("");
        blocks.add(new ReasoningContent(
                summary,
                encryptedContent.isBlank() ? Map.of() : Map.of("encrypted_content", encryptedContent)));
    }

    private static String required(@Nullable String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private record Continuation(@Nullable String responseId, int firstMessageIndex) { }

    public static final class Builder {
        private @Nullable HttpClient httpClient;
        private @Nullable String apiKey;
        private @Nullable String modelName;
        private @Nullable Duration timeout;

        private Builder() { }

        public Builder httpClient(HttpClient value) {
            httpClient = Objects.requireNonNull(value);
            return this;
        }

        public Builder apiKey(String value) {
            apiKey = value;
            return this;
        }

        public Builder modelName(String value) {
            modelName = value;
            return this;
        }

        public Builder timeout(Duration value) {
            timeout = value;
            return this;
        }

        public OpenAiResponsesChatModel build() {
            return new OpenAiResponsesChatModel(this);
        }
    }
}
