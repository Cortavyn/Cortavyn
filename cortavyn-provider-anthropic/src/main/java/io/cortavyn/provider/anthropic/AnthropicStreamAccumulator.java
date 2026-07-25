package io.cortavyn.provider.anthropic;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Normalizes Anthropic's Messages SSE event stream. */
final class AnthropicStreamAccumulator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final StringBuilder text = new StringBuilder();
    private final StringBuilder thinking = new StringBuilder();
    private final Map<Integer, Tool> tools = new HashMap<>();
    private @Nullable String model; private @Nullable String stopReason; private @Nullable Integer inputTokens; private @Nullable Integer outputTokens;
    List<ChatStreamEvent> accept(String line) {
        if (!line.startsWith("data:")) return List.of();
        try {
            JsonNode root = JSON.readTree(line.substring(5).trim()); String type = root.path("type").asText(); List<ChatStreamEvent> events = new ArrayList<>();
            if ("message_start".equals(type)) { JsonNode message = root.path("message"); model = message.path("model").textValue(); inputTokens = message.path("usage").path("input_tokens").isMissingNode() ? null : message.path("usage").path("input_tokens").asInt(); }
            if ("content_block_start".equals(type) && "tool_use".equals(root.path("content_block").path("type").asText())) { Tool tool = tools.computeIfAbsent(root.path("index").asInt(), ignored -> new Tool()); tool.id = root.path("content_block").path("id").asText(); tool.name = root.path("content_block").path("name").asText(); }
            if ("content_block_delta".equals(type)) { JsonNode delta = root.path("delta"); String value = delta.path("text").textValue(); if (value != null) { text.append(value); events.add(new ChatTextDelta(value)); } String thought = delta.path("thinking").textValue(); if (thought != null) { thinking.append(thought); events.add(new ChatReasoningDelta(thought)); } String json = delta.path("partial_json").textValue(); if (json != null) { Tool tool = tools.computeIfAbsent(root.path("index").asInt(), ignored -> new Tool()); tool.arguments.append(json); if (tool.id != null && tool.name != null) events.add(new ChatToolCallDelta(tool.id, tool.name, json)); } }
            if ("message_delta".equals(type)) { stopReason = root.path("delta").path("stop_reason").textValue(); JsonNode usage = root.path("usage"); if (!usage.isMissingNode()) outputTokens = usage.path("output_tokens").asInt(); }
            return events;
        } catch (Exception exception) { throw new AnthropicResponseException("Anthropic returned an invalid stream event", exception); }
    }
    ChatCompletion complete() {
        List<ToolCall> calls = new ArrayList<>(); for (Tool tool : tools.values()) { if (tool.id == null || tool.name == null) throw new AnthropicResponseException("Anthropic returned an incomplete streamed tool call"); try { calls.add(new ToolCall(tool.id, tool.name, JSON.readValue(tool.arguments.toString(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }))); } catch (Exception exception) { throw new AnthropicResponseException("Anthropic returned invalid streamed tool arguments", exception); } }
        List<io.cortavyn.model.api.ChatContent> blocks = new ArrayList<>(); blocks.add(new TextContent(text.toString())); if (!thinking.isEmpty()) blocks.add(new ReasoningContent(thinking.toString())); TokenUsage usage = inputTokens == null || outputTokens == null ? null : new TokenUsage(inputTokens, outputTokens, inputTokens + outputTokens);
        return new ChatCompletion(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, text.toString(), blocks, null, calls), new ChatResponseMetadata(model, null, stopReason, usage), Map.of()));
    }
    private static final class Tool { private @Nullable String id; private @Nullable String name; private final StringBuilder arguments = new StringBuilder(); }
}
