package io.cortavyn.model.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Parser and normalizer for the OpenAI Chat Completions SSE dialect. */
public final class OpenAiChatStreamAccumulator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final StringBuilder text = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private final Map<Integer, Tool> tools = new HashMap<>();
    private @Nullable String model;
    private @Nullable String finishReason;
    private @Nullable TokenUsage usage;

    public List<ChatStreamEvent> accept(String line) {
        if (!line.startsWith("data:")) return List.of();
        String data = line.substring(5).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) return List.of();
        try {
            JsonNode root = JSON.readTree(data);
            model = root.path("model").textValue() == null ? model : root.path("model").textValue();
            JsonNode wireUsage = root.path("usage");
            if (!wireUsage.isMissingNode()) usage = new TokenUsage(wireUsage.path("prompt_tokens").asInt(), wireUsage.path("completion_tokens").asInt(), wireUsage.path("total_tokens").asInt());
            JsonNode choice = root.path("choices").path(0);
            if (choice.isMissingNode()) return List.of();
            if (choice.path("finish_reason").textValue() != null) finishReason = choice.path("finish_reason").textValue();
            JsonNode delta = choice.path("delta");
            List<ChatStreamEvent> events = new ArrayList<>();
            String value = delta.path("content").textValue();
            if (value != null && !value.isEmpty()) { text.append(value); events.add(new ChatTextDelta(value)); }
            String thought = delta.path("reasoning_content").textValue();
            if (thought == null) thought = delta.path("reasoning").textValue();
            if (thought != null && !thought.isEmpty()) { reasoning.append(thought); events.add(new ChatReasoningDelta(thought)); }
            for (JsonNode call : delta.path("tool_calls")) {
                int index = call.path("index").asInt();
                Tool tool = tools.computeIfAbsent(index, ignored -> new Tool());
                if (call.path("id").textValue() != null) tool.id = call.path("id").textValue();
                JsonNode function = call.path("function");
                if (function.path("name").textValue() != null) tool.name = function.path("name").textValue();
                String arguments = function.path("arguments").textValue();
                if (arguments != null) tool.arguments.append(arguments);
                if (tool.id != null && tool.name != null && arguments != null) events.add(new ChatToolCallDelta(tool.id, tool.name, arguments));
            }
            return events;
        } catch (Exception exception) { throw new IllegalArgumentException("Invalid OpenAI-compatible stream event", exception); }
    }

    public ChatCompletion complete() {
        List<ChatContent> blocks = new ArrayList<>();
        blocks.add(new TextContent(text.toString()));
        if (!reasoning.isEmpty()) blocks.add(new ReasoningContent(reasoning.toString()));
        List<ToolCall> calls = new ArrayList<>();
        for (Tool tool : tools.values()) {
            if (tool.id == null || tool.name == null) throw new IllegalArgumentException("Incomplete tool call in stream");
            try { calls.add(new ToolCall(tool.id, tool.name, JSON.readValue(tool.arguments.toString(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }))); }
            catch (Exception exception) { throw new IllegalArgumentException("Invalid streamed tool-call arguments", exception); }
        }
        return new ChatCompletion(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, text.toString(), blocks, null, calls), new ChatResponseMetadata(model, null, finishReason, usage), Map.of()));
    }

    private static final class Tool { private @Nullable String id; private @Nullable String name; private final StringBuilder arguments = new StringBuilder(); }
}
