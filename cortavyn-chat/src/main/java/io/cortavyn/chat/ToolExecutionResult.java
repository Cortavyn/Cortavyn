package io.cortavyn.chat;
import java.util.Objects;
/** Serialized result returned to the model after a tool invocation. */
public record ToolExecutionResult(String content, boolean error) { public ToolExecutionResult { Objects.requireNonNull(content, "content must not be null"); } public static ToolExecutionResult success(String content) { return new ToolExecutionResult(content, false); } public static ToolExecutionResult failure(String content) { return new ToolExecutionResult(content, true); } }
