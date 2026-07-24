package io.cortavyn.model.api;
import org.jspecify.annotations.Nullable;
/** Provider-neutral response details for tracing, accounting, and control flow. */
public record ChatResponseMetadata(@Nullable String modelName, @Nullable String requestId, @Nullable String finishReason, @Nullable TokenUsage usage) { public static ChatResponseMetadata empty() { return new ChatResponseMetadata(null, null, null, null); } }
