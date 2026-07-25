package io.cortavyn.provider.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cortavyn.model.api.ChatCompletion;
import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatReasoningDelta;
import io.cortavyn.model.api.ChatResponse;
import io.cortavyn.model.api.ChatResponseMetadata;
import io.cortavyn.model.api.ChatStreamEvent;
import io.cortavyn.model.api.ChatTextDelta;
import io.cortavyn.model.api.ReasoningContent;
import io.cortavyn.model.api.TextContent;
import io.cortavyn.model.api.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Normalizes Ollama's newline-delimited JSON chat stream. */
final class OllamaStreamAccumulator {
    private static final ObjectMapper JSON = new ObjectMapper(); private final StringBuilder text = new StringBuilder(); private final StringBuilder thinking = new StringBuilder(); private @Nullable String model; private @Nullable String reason; private @Nullable TokenUsage usage;
    List<ChatStreamEvent> accept(String line) { if (line.isBlank()) return List.of(); try { JsonNode root = JSON.readTree(line); model = root.path("model").textValue() == null ? model : root.path("model").textValue(); if (root.path("done").asBoolean()) { reason = root.path("done_reason").textValue(); if (!root.path("prompt_eval_count").isMissingNode()) usage = new TokenUsage(root.path("prompt_eval_count").asInt(), root.path("eval_count").asInt(), root.path("prompt_eval_count").asInt() + root.path("eval_count").asInt()); } JsonNode message = root.path("message"); List<ChatStreamEvent> events = new ArrayList<>(); String value = message.path("content").textValue(); if (value != null && !value.isEmpty()) { text.append(value); events.add(new ChatTextDelta(value)); } String thought = message.path("thinking").textValue(); if (thought != null && !thought.isEmpty()) { thinking.append(thought); events.add(new ChatReasoningDelta(thought)); } return events; } catch (Exception exception) { throw new OllamaResponseException("Ollama returned an invalid stream event", exception); } }
    ChatCompletion complete() { List<io.cortavyn.model.api.ChatContent> blocks = new ArrayList<>(); blocks.add(new TextContent(text.toString())); if (!thinking.isEmpty()) blocks.add(new ReasoningContent(thinking.toString())); return new ChatCompletion(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, text.toString(), blocks, null, List.of()), new ChatResponseMetadata(model, null, reason, usage), Map.of())); }
}
