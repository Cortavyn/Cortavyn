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

    /**
     * Creates a typed tool from explicit provider-visible metadata.
     *
     * @param name stable function name sent to the model
     * @param description instruction that helps the model decide when to call the tool
     * @param argumentsType record whose components become the JSON Schema input properties
     * @param executor asynchronous application code receiving deserialized record arguments
     * @return a tool that returns invalid model arguments as an error result instead of throwing
     */
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

    /**
     * Creates an untyped tool whose executor receives the current agent runtime.
     *
     * @param definition provider-visible schema and descriptive metadata
     * @param executor application code receiving raw arguments and the run context
     * @return a runtime-aware tool
     */
    public static ChatTool withRuntime(ToolDefinition definition, RuntimeToolExecutor executor) {
        Objects.requireNonNull(executor, "executor must not be null");
        return new ChatTool(definition, new ToolExecutor() {
            @Override public java.util.concurrent.CompletionStage<ToolExecutionResult> execute(io.cortavyn.model.api.ToolCall call) { return executor.execute(call, ToolRuntime.ephemeral(call.id())); }
            @Override public java.util.concurrent.CompletionStage<ToolExecutionResult> execute(io.cortavyn.model.api.ToolCall call, ToolRuntime runtime) { return executor.execute(call, runtime); }
        });
    }

    /**
     * Creates a typed, runtime-aware tool.
     *
     * @param name stable function name sent to the model
     * @param description instruction that helps the model decide when to call the tool
     * @param argumentsType record whose components become the JSON Schema input properties
     * @param executor asynchronous application code receiving typed arguments and run context
     * @return a runtime-aware tool
     */
    public static <T> ChatTool typed(String name, String description, Class<T> argumentsType, RuntimeTypedToolExecutor<T> executor) {
        Objects.requireNonNull(argumentsType, "argumentsType must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        ToolDefinition definition = new ToolDefinition(name, description, TypedToolSchema.forRecord(argumentsType));
        return withRuntime(definition, (call, runtime) -> {
            try {
                return executor.execute(OBJECT_MAPPER.convertValue(call.arguments(), argumentsType), runtime);
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

    /** Creates a runtime-aware typed tool using annotations on its argument record. */
    public static <T> ChatTool typed(Class<T> argumentsType, RuntimeTypedToolExecutor<T> executor) {
        Objects.requireNonNull(argumentsType, "argumentsType must not be null");
        ToolName name = argumentsType.getAnnotation(ToolName.class);
        ToolDescription description = argumentsType.getAnnotation(ToolDescription.class);
        if (name == null || description == null) {
            throw new IllegalArgumentException("annotated typed tools require @ToolName and @ToolDescription on " + argumentsType.getName());
        }
        return typed(name.value(), description.value(), argumentsType, executor);
    }
}
