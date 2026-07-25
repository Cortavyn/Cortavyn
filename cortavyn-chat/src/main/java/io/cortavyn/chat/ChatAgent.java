package io.cortavyn.chat;

import io.cortavyn.model.api.ChatGenerationParameters;
import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.model.api.ToolCall;
import io.cortavyn.model.api.ToolDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A LangChain-style chat agent: it invokes a chat model, executes requested application tools,
 * and continues until the model returns an assistant message without tool calls.
 */
public final class ChatAgent implements ChatSession {
    private final ChatModel model;
    private final Map<String, ToolExecutor> tools;
    private final List<ToolDefinition> definitions;
    private final int maxIterations;

    private ChatAgent(Builder builder) {
        model = Objects.requireNonNull(builder.model, "model must not be null");
        maxIterations = builder.maxIterations;
        if (maxIterations <= 0) throw new IllegalArgumentException("maxIterations must be positive");
        Map<String, ToolExecutor> executors = new HashMap<>();
        List<ToolDefinition> toolDefinitions = new ArrayList<>();
        for (ChatTool tool : builder.tools) {
            String name = tool.definition().name();
            if (executors.putIfAbsent(name, tool.executor()) != null) throw new IllegalArgumentException("duplicate tool name: " + name);
            toolDefinitions.add(tool.definition());
        }
        tools = Map.copyOf(executors);
        definitions = List.copyOf(toolDefinitions);
    }

    public static Builder builder(ChatModel model) {
        return new Builder(model);
    }

    @Override
    public CompletionStage<Conversation> reply(Conversation conversation, ChatMessage userMessage) {
        Objects.requireNonNull(conversation, "conversation must not be null");
        Objects.requireNonNull(userMessage, "userMessage must not be null");
        List<ChatMessage> messages = new ArrayList<>(conversation.messages());
        messages.add(userMessage);
        return run(conversation.id(), messages, 0);
    }

    private CompletionStage<Conversation> run(String conversationId, List<ChatMessage> messages, int iteration) {
        if (iteration >= maxIterations) return CompletableFuture.failedStage(new IllegalStateException("agent exceeded maxIterations: " + maxIterations));
        return model.complete(new ChatRequest(messages, definitions, ChatGenerationParameters.defaults(), Map.of()))
                .thenCompose(response -> {
                    List<ChatMessage> updated = new ArrayList<>(messages);
                    updated.add(response.message());
                    if (response.message().toolCalls().isEmpty()) return CompletableFuture.completedFuture(new Conversation(conversationId, updated));
                    return execute(response.message().toolCalls()).thenCompose(results -> {
                        updated.addAll(results);
                        return run(conversationId, updated, iteration + 1);
                    });
                });
    }

    private CompletionStage<List<ChatMessage>> execute(List<ToolCall> calls) {
        List<CompletableFuture<ChatMessage>> futures = calls.stream().map(call -> execute(call).toCompletableFuture()).toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
    }

    private CompletionStage<ChatMessage> execute(ToolCall call) {
        ToolExecutor executor = tools.get(call.name());
        if (executor == null) return CompletableFuture.completedFuture(ChatMessage.toolResult(call.id(), "Unknown tool: " + call.name()));
        try {
            return executor.execute(call)
                    .handle((result, failure) -> ChatMessage.toolResult(call.id(), failure == null ? result.content() : "Tool failed: " + failure.getMessage()));
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(ChatMessage.toolResult(call.id(), "Tool failed: " + failure.getMessage()));
        }
    }

    public static final class Builder {
        private final ChatModel model;
        private List<ChatTool> tools = List.of();
        private int maxIterations = 10;

        private Builder(ChatModel model) { this.model = Objects.requireNonNull(model, "model must not be null"); }
        public Builder tools(ChatTool... value) { tools = List.of(value); return this; }
        public Builder maxIterations(int value) { maxIterations = value; return this; }
        public ChatAgent build() { return new ChatAgent(this); }
    }
}
