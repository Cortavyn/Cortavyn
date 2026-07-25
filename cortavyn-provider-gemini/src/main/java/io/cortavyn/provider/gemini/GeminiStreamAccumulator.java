package io.cortavyn.provider.gemini;

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
import io.cortavyn.model.api.ChatToolCallDelta;
import io.cortavyn.model.api.ReasoningContent;
import io.cortavyn.model.api.TextContent;
import io.cortavyn.model.api.TokenUsage;
import io.cortavyn.model.api.ToolCall;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Normalizes Gemini's streamGenerateContent SSE responses. */
final class GeminiStreamAccumulator {
    private static final ObjectMapper JSON = new ObjectMapper(); private final String model; private final StringBuilder text = new StringBuilder(); private final StringBuilder thought = new StringBuilder(); private final List<ToolCall> calls = new ArrayList<>(); private @Nullable String finish; private @Nullable TokenUsage usage;
    GeminiStreamAccumulator(String model) { this.model = model; }
    List<ChatStreamEvent> accept(String line) {
        if (line.isBlank() || line.startsWith("event:")) return List.of(); String data = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
        try { JsonNode root = JSON.readTree(data); JsonNode candidate = root.path("candidates").path(0); if (candidate.isMissingNode()) return List.of(); if (candidate.path("finishReason").textValue() != null) finish = candidate.path("finishReason").textValue(); JsonNode wireUsage = root.path("usageMetadata"); if (!wireUsage.isMissingNode()) usage = new TokenUsage(wireUsage.path("promptTokenCount").asInt(), wireUsage.path("candidatesTokenCount").asInt(), wireUsage.path("totalTokenCount").asInt()); List<ChatStreamEvent> events = new ArrayList<>(); for (JsonNode part : candidate.path("content").path("parts")) { String value = part.path("text").textValue(); if (value != null) { if (part.path("thought").asBoolean()) { thought.append(value); events.add(new ChatReasoningDelta(value)); } else { text.append(value); events.add(new ChatTextDelta(value)); } } JsonNode call = part.path("functionCall"); if (!call.isMissingNode()) { String id = call.path("id").asText(call.path("name").asText()); String name = call.path("name").asText(); String arguments = JSON.writeValueAsString(call.path("args")); calls.add(new ToolCall(id, name, JSON.convertValue(call.path("args"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }))); events.add(new ChatToolCallDelta(id, name, arguments)); } } return events; } catch (Exception exception) { throw new GeminiResponseException("Gemini returned an invalid stream event", exception); }
    }
    ChatCompletion complete() { List<io.cortavyn.model.api.ChatContent> blocks = new ArrayList<>(); blocks.add(new TextContent(text.toString())); if (!thought.isEmpty()) blocks.add(new ReasoningContent(thought.toString())); return new ChatCompletion(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, text.toString(), blocks, null, calls), new ChatResponseMetadata(model, null, finish, usage), Map.of())); }
}
