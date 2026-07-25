package io.cortavyn.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cortavyn.model.api.ToolDefinition;
import java.util.concurrent.CompletableFuture;
import java.util.Objects;

/** An application-owned tool that an agent may expose to a chat model. */
public record ChatTool(ToolDefinition definition, ToolExecutor executor) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public ChatTool {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
    }

    /** Creates a tool from a record input type and explicit provider-visible metadata. */
    public static <T> ChatTool typed(String name, String description, Class<T> argumentsType, TypedToolExecutor<T> executor) {
        Objects.requireNonNull(argumentsType, "argumentsType must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        ToolDefinition definition = new ToolDefinition(name, description, TypedToolSchema.forRecord(argumentsType));
        return new ChatTool(definition, call -> {
            try {
                return executor.execute(OBJECT_MAPPER.convertValue(call.arguments(), argumentsType));
            } catch (IllegalArgumentException exception) {
                return CompletableFuture.completedFuture(ToolExecutionResult.failure("Invalid arguments for tool '" + name + "': " + exception.getMessage()));
            }
        });
    }

    /** Creates a tool using {@link ToolName} and {@link ToolDescription} on its argument record. */
    public static <T> ChatTool typed(Class<T> argumentsType, TypedToolExecutor<T> executor) {
        Objects.requireNonNull(argumentsType, "argumentsType must not be null");
        ToolName name = argumentsType.getAnnotation(ToolName.class);
        ToolDescription description = argumentsType.getAnnotation(ToolDescription.class);
        if (name == null || description == null) {
            throw new IllegalArgumentException("annotated typed tools require @ToolName and @ToolDescription on " + argumentsType.getName());
        }
        return typed(name.value(), description.value(), argumentsType, executor);
    }
}
