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
import org.jspecify.annotations.Nullable;

/**
 * A LangChain-style agent loop over a {@link ChatModel}.
 *
 * <p>For each model response, the agent appends the assistant message, executes every requested
 * tool concurrently, appends their tool-result messages, and invokes the model again. The loop
 * ends only when the assistant returns no tool calls. Unknown tools and execution failures are
 * returned to the model as tool-result text, so the model can recover or explain the failure.</p>
 */
public final class ChatAgent implements ChatSession {
    private final ChatModel model;
    private final Map<String, ToolExecutor> tools;
    private final List<ToolDefinition> definitions;
    private final int maxIterations;
    private final ToolRuntime runtime;

    private ChatAgent(Builder builder) {
        model = Objects.requireNonNull(builder.model, "model must not be null");
        maxIterations = builder.maxIterations;
        runtime = builder.runtime == null ? ToolRuntime.ephemeral("chat-agent") : builder.runtime;
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

    /**
     * Starts construction of an agent.
     *
     * @param model the provider-neutral model used for every iteration
     * @return a mutable builder for the agent configuration
     */
    public static Builder builder(ChatModel model) {
        return new Builder(model);
    }

    @Override
    /**
     * Adds a user message to a conversation and runs the tool loop asynchronously.
     *
     * @param conversation prior conversation state; it is never mutated
     * @param userMessage next user input, normally with role {@code USER}
     * @return a new conversation containing assistant and tool-result messages
     */
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
            return executor.execute(call, runtime)
                    .handle((result, failure) -> failure == null
                            ? ChatMessage.toolResult(call.id(), result.contentBlocks(), result.error(), result.metadata())
                            : ChatMessage.toolResult(call.id(), "Tool failed: " + failure.getMessage()));
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(ChatMessage.toolResult(call.id(), "Tool failed: " + failure.getMessage()));
        }
    }

    public static final class Builder {
        private final ChatModel model;
        private List<ChatTool> tools = List.of();
        private int maxIterations = 10;
        private @Nullable ToolRuntime runtime;

        private Builder(ChatModel model) { this.model = Objects.requireNonNull(model, "model must not be null"); }
        /** @param value application-owned tools exposed to the model, keyed by their definition name */
        public Builder tools(ChatTool... value) { tools = List.of(value); return this; }
        /** @param value maximum model/tool rounds before the returned stage fails; defaults to 10 */
        public Builder maxIterations(int value) { maxIterations = value; return this; }
        /** @param value run-scoped context shared by runtime-aware tools; defaults to an ephemeral runtime */
        public Builder runtime(ToolRuntime value) { runtime = Objects.requireNonNull(value, "runtime must not be null"); return this; }
        /** @return an immutable, reusable agent */
        public ChatAgent build() { return new ChatAgent(this); }
    }
}
