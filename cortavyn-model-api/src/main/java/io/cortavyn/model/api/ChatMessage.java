package io.cortavyn.model.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A provider-neutral chat message. */
public record ChatMessage(
        ChatMessageRole role,
        String content,
        List<ChatContent> contentBlocks,
        @Nullable String toolCallId,
        List<ToolCall> toolCalls,
        Map<String, Object> metadata) {
    public ChatMessage(
            ChatMessageRole role,
            String content,
            List<ChatContent> contentBlocks,
            @Nullable String toolCallId,
            List<ToolCall> toolCalls,
            Map<String, Object> metadata) {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(content, "content must not be null");
        contentBlocks = List.copyOf(Objects.requireNonNull(contentBlocks, "contentBlocks must not be null"));
        toolCalls = List.copyOf(Objects.requireNonNull(toolCalls, "toolCalls must not be null"));
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null"));
        if (role == ChatMessageRole.TOOL && (toolCallId == null || toolCallId.isBlank())) throw new IllegalArgumentException("TOOL messages require toolCallId");
        if (role != ChatMessageRole.ASSISTANT && !toolCalls.isEmpty()) throw new IllegalArgumentException("only ASSISTANT messages may contain tool calls");
        this.role = role;
        this.content = content;
        this.contentBlocks = contentBlocks;
        this.toolCallId = toolCallId;
        this.toolCalls = toolCalls;
        this.metadata = metadata;
    }

    public ChatMessage(ChatMessageRole role, String content) {
        this(role, content, List.of(new TextContent(content)), null, List.of(), Map.of());
    }

    public ChatMessage(ChatMessageRole role, List<ChatContent> contentBlocks) {
        this(role, textContent(contentBlocks), contentBlocks, null, List.of(), Map.of());
    }

    public ChatMessage(
            ChatMessageRole role,
            String content,
            List<ChatContent> contentBlocks,
            @Nullable String toolCallId,
            List<ToolCall> toolCalls) {
        this(role, content, contentBlocks, toolCallId, toolCalls, Map.of());
    }

    public static ChatMessage toolResult(String toolCallId, String content) {
        return new ChatMessage(ChatMessageRole.TOOL, content, List.of(new TextContent(content)), toolCallId, List.of(), Map.of());
    }
    private static String textContent(List<ChatContent> contentBlocks) { return contentBlocks.stream().filter(TextContent.class::isInstance).map(TextContent.class::cast).map(TextContent::text).reduce("", String::concat); }
}
