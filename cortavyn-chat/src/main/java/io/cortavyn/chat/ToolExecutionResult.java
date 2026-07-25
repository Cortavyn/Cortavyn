package io.cortavyn.chat;

import io.cortavyn.model.api.ChatContent;
import io.cortavyn.model.api.TextContent;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The portable, optionally structured result returned by an application-owned tool. */
public record ToolExecutionResult(String content, List<ChatContent> contentBlocks, boolean error, Map<String, Object> metadata) {
    public ToolExecutionResult {
        Objects.requireNonNull(content, "content must not be null");
        contentBlocks = List.copyOf(Objects.requireNonNull(contentBlocks, "contentBlocks must not be null"));
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null"));
    }

    public ToolExecutionResult(String content, boolean error) {
        this(content, List.of(new TextContent(content)), error, Map.of());
    }

    public static ToolExecutionResult success(String content) { return new ToolExecutionResult(content, false); }
    public static ToolExecutionResult failure(String content) { return new ToolExecutionResult(content, true); }
    public static ToolExecutionResult success(List<ChatContent> contentBlocks) { return new ToolExecutionResult(textContent(contentBlocks), contentBlocks, false, Map.of()); }
    public static ToolExecutionResult failure(List<ChatContent> contentBlocks) { return new ToolExecutionResult(textContent(contentBlocks), contentBlocks, true, Map.of()); }
    public ToolExecutionResult withMetadata(Map<String, Object> value) { return new ToolExecutionResult(content, contentBlocks, error, value); }

    private static String textContent(List<ChatContent> contentBlocks) {
        return List.copyOf(contentBlocks).stream().filter(TextContent.class::isInstance).map(TextContent.class::cast).map(TextContent::text).reduce("", String::concat);
    }
}
