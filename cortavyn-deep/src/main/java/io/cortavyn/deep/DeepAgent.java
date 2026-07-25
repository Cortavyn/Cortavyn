package io.cortavyn.deep;

import io.cortavyn.chat.ChatTool;
import io.cortavyn.chat.Conversation;
import io.cortavyn.chat.ToolExecutionResult;
import io.cortavyn.chat.ToolRuntime;
import io.cortavyn.graph.Checkpoint;
import io.cortavyn.graph.GraphState;
import io.cortavyn.graph.GraphStatus;
import io.cortavyn.graph.Interrupt;
import io.cortavyn.graph.StateChannel;
import io.cortavyn.graph.StateGraph;
import io.cortavyn.graph.StateSchema;
import io.cortavyn.graph.StateUpdate;
import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.model.api.ChatGenerationParameters;
import io.cortavyn.model.api.ToolCall;
import io.cortavyn.model.api.ToolDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/** Durable, tool-using agent harness with a virtual workspace and task planning. */
public final class DeepAgent {
    private final ChatModel model;
    private final List<ChatTool> tools;
    private final String systemPrompt;
    // A caller-supplied workspace is intentionally shared. Without one, each thread receives
    // an isolated in-memory workspace from threadWorkspaces below.
    private final @org.jspecify.annotations.Nullable DeepWorkspace configuredWorkspace;
    private final ConcurrentMap<String, DeepWorkspace> threadWorkspaces = new ConcurrentHashMap<>();
    private final ContextPolicy contextPolicy;
    private final DeepMemory memory;
    private final String memoryNamespace;
    private final List<DeepSkill> skills;
    private final Map<String, DeepAgent> subagents;
    private final SubagentRegistry subagentRegistry;
    private final ApprovalPolicy approvalPolicy;
    private final DeepRunStore runStore;
    private final List<McpToolSource> mcpSources;
    private final DeepTodoStore todoStore;
    private final @org.jspecify.annotations.Nullable Sandbox sandbox;
    // The plan is deliberately internal: it gives normal invoke/resume calls graph checkpoints
    // without requiring an application to construct a StateGraph itself.
    private final DeepAgentPlan plan;
    private static final String GRAPH_MESSAGES = "deepMessages";
    private static final String GRAPH_ITERATION = "deepIteration";
    private static final String GRAPH_RUN = "deepRun";

    private DeepAgent(Builder builder) {
        model = builder.model;
        tools = List.copyOf(builder.tools);
        systemPrompt = builder.systemPrompt;
        configuredWorkspace = builder.workspace;
        contextPolicy = builder.contextPolicy;
        memory = builder.memory;
        memoryNamespace = builder.memoryNamespace;
        skills = List.copyOf(builder.skills);
        Map<String, DeepAgent> configuredSubagents = new LinkedHashMap<>();
        for (DeepSubagent subagent : builder.subagents) {
            // A specialist gets a fresh agent/context. A configured parent workspace may be
            // wrapped so a specialist can only see the paths delegated to it.
            Builder child = DeepAgent.builder(model).systemPrompt(subagent.systemPrompt()).tools(subagent.tools().toArray(ChatTool[]::new)).contextPolicy(contextPolicy).approvalPolicy(subagent.approvalPolicy() == null ? builder.approvalPolicy : subagent.approvalPolicy());
            if (configuredWorkspace != null) child.workspace(subagent.workspacePermissions().isEmpty() ? configuredWorkspace : new PermissionedWorkspace(configuredWorkspace, subagent.workspacePermissions()));
            configuredSubagents.put(subagent.name(), child.build());
        }
        subagents = Map.copyOf(configuredSubagents);
        approvalPolicy = builder.approvalPolicy;
        runStore = builder.runStore;
        mcpSources = List.copyOf(builder.mcpSources);
        todoStore = builder.todoStore;
        sandbox = builder.sandbox;
        subagentRegistry = new SubagentRegistry(builder.taskStore, (name, prompt) -> {
            DeepAgent subagent = subagents.get(name);
            if (subagent == null) return CompletableFuture.failedStage(new IllegalArgumentException("unknown subagent: " + name));
            return subagent.invoke("subagent-" + java.util.UUID.randomUUID(), prompt).thenApply(run -> run.conversation().messages().getLast().content());
        });
        // Only messages, the current loop counter and the last DeepRun cross the graph-node
        // boundary. Tool details remain in the conversation and the durable DeepPendingRun.
        StateSchema<GraphState> schema = StateSchema.builder(GraphState.adapter())
                .channel(GRAPH_MESSAGES, StateChannel.lastValue()).channel(GRAPH_ITERATION, StateChannel.lastValue()).channel(GRAPH_RUN, StateChannel.lastValue()).build();
        plan = new DeepAgentPlan(new StateGraph<>(schema).addNode("deep-loop", this::executeGraphNode).addEdge(StateGraph.START, "deep-loop").addEdge("deep-loop", StateGraph.END).compile());
    }
    public static Builder builder(ChatModel model) { return new Builder(model); }
    /** Returns durable checkpoints created by this agent's internal graph for one thread. */
    public List<Checkpoint> history(String threadId) { return plan.graph().history(threadId); }
    public CompletionStage<DeepRun> invoke(String threadId, String input) {
        Objects.requireNonNull(input, "input must not be null");
        return start(threadId, input, ignored -> { }, true);
    }
    private CompletionStage<DeepRun> start(String threadId, String input, Consumer<DeepEvent> events, boolean graphDriven) {
        return memory.load(memoryNamespace).thenCompose(loadedMemory -> {
        // Build the initial context once. Skill instructions are intentionally deferred; only
        // their catalogue metadata enters the prompt until the model calls load_skill.
        List<ChatMessage> initial = new ArrayList<>();
        if (!systemPrompt.isBlank()) initial.add(new ChatMessage(ChatMessageRole.SYSTEM, systemPrompt));
        if (!loadedMemory.isBlank()) initial.add(new ChatMessage(ChatMessageRole.SYSTEM, "Persistent memory:\n" + loadedMemory));
        if (!skills.isEmpty()) initial.add(new ChatMessage(ChatMessageRole.SYSTEM, "Available skills (load their instructions when relevant):\n" + skills.stream().map(skill -> "- " + skill.name() + ": " + skill.description()).collect(java.util.stream.Collectors.joining("\n"))));
        initial.add(new ChatMessage(ChatMessageRole.SYSTEM, "Use write_todos for multi-step work. Use the workspace tools for large intermediate results and verify changes."));
        initial.add(new ChatMessage(ChatMessageRole.USER, input));
        // The stream needs per-step events, so it drives the same loop directly. Invoke uses the
        // graph wrapper to persist graph checkpoints and convert approvals into interrupts.
        return graphDriven ? executeGraph(threadId, initial, 0) : run(threadId, initial, 0, events);
        });
    }
    public CompletionStage<DeepRun> invoke(DeepRequest request) { return invoke(request.threadId(), request.input()); }
    /** Starts a cold run and emits progress plus a terminal completion, interrupt, or failure event. */
    public java.util.concurrent.Flow.Publisher<DeepEvent> stream(DeepRequest request) {
        return subscriber -> {
            java.util.concurrent.SubmissionPublisher<DeepEvent> publisher = new java.util.concurrent.SubmissionPublisher<>();
            publisher.subscribe(subscriber);
            start(request.threadId(), request.input(), publisher::submit, false).whenComplete((run, failure) -> {
                if (failure != null) publisher.submit(new DeepEvent.Failed(failure));
                else publisher.submit(run.interrupt() == null ? new DeepEvent.Completed(run) : new DeepEvent.Interrupted(run));
                publisher.close();
            });
        };
    }
    /** Continues a paused run after one decision per pending action, in request order. */
    public CompletionStage<DeepRun> resume(String threadId, List<ApprovalDecision> decisions) {
        return runStore.get(threadId).thenCompose(found -> {
        DeepPendingRun state = found.orElseThrow(() -> new IllegalArgumentException("no pending approval for thread: " + threadId));
        if (decisions.size() != state.calls().size()) return CompletableFuture.failedStage(new IllegalArgumentException("one decision is required for each pending action"));
        // Restore the virtual files before applying approved calls. This lets a new JVM continue
        // an approval run whose previous agent instance no longer exists.
        return restoreWorkspace(threadId, state.workspaceSnapshot()).thenCompose(restored -> {
            List<ChatMessage> messages = new ArrayList<>(state.messages());
            List<CompletableFuture<ChatMessage>> results = new ArrayList<>();
            for (int index = 0; index < state.calls().size(); index++) results.add(resolve(threadId, state.calls().get(index), decisions.get(index), ignored -> { }).toCompletableFuture());
            return CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).thenCompose(ignored -> { results.forEach(result -> messages.add(result.join())); return runStore.delete(threadId).thenCompose(deleted -> executeGraph(threadId, messages, state.iteration() + 1)); });
        });
        });
    }
    private CompletionStage<DeepRun> run(String threadId, List<ChatMessage> messages, int iteration, Consumer<DeepEvent> events) {
        if (iteration >= contextPolicy.maxIterations()) return CompletableFuture.failedStage(new IllegalStateException("deep agent exceeded maxIterations: " + contextPolicy.maxIterations()));
        ChatTool[] available = allTools(threadId); List<ToolDefinition> definitions = java.util.Arrays.stream(available).map(ChatTool::definition).toList();
        // Compact before every model turn: tool results can otherwise grow the conversation far
        // beyond a provider context window during long-running tasks.
        return compactHistory(messages).thenCompose(activeMessages -> model.complete(new ChatRequest(activeMessages, definitions, ChatGenerationParameters.defaults(), Map.of())).thenCompose(response -> {
            List<ChatMessage> updated = new ArrayList<>(activeMessages); updated.add(response.message()); events.accept(new DeepEvent.Message(response.message())); List<ToolCall> calls = response.message().toolCalls();
            if (calls.isEmpty()) return todoStore.read(threadId).thenApply(todos -> new DeepRun(threadId, new Conversation(threadId, updated), workspaceFor(threadId), todos, null));
            List<ToolCall> sensitive = calls.stream().filter(call -> approvalPolicy.requiresApproval(call.name())).toList();
            if (!sensitive.isEmpty()) {
                // Non-sensitive calls can still make progress. Only the sensitive subset is
                // checkpointed and shown to the reviewer as one bundled interrupt.
                List<ToolCall> immediate = calls.stream().filter(call -> !approvalPolicy.requiresApproval(call.name())).toList();
                List<CompletableFuture<ChatMessage>> immediateResults = immediate.stream().map(call -> execute(threadId, call, events)).map(stage -> stage.toCompletableFuture()).toList();
                return CompletableFuture.allOf(immediateResults.toArray(CompletableFuture[]::new)).thenCompose(ignored -> { immediateResults.forEach(result -> updated.add(result.join())); List<ApprovalRequest> actions = sensitive.stream().map(call -> new ApprovalRequest(call.name(), call.arguments(), approvalPolicy.decisionsFor(call.name()))).toList(); DeepInterrupt interrupt = new DeepInterrupt(actions); events.accept(new DeepEvent.ApprovalRequested(interrupt)); return snapshotWorkspace(threadId).thenCompose(snapshot -> runStore.save(new DeepPendingRun(threadId, updated, sensitive, iteration, snapshot.orElse(null)))).thenCompose(saved -> todoStore.read(threadId).thenApply(todos -> new DeepRun(threadId, new Conversation(threadId, updated), workspaceFor(threadId), todos, interrupt))); });
            }
            List<CompletableFuture<ChatMessage>> results = calls.stream().map(call -> execute(threadId, call, events)).map(stage -> stage.toCompletableFuture()).toList();
            return CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).thenCompose(ignored -> { results.forEach(result -> updated.add(result.join())); return run(threadId, updated, iteration + 1, events); });
        }));
    }
    private java.util.concurrent.CompletionStage<? extends io.cortavyn.graph.NodeResult> executeGraphNode(GraphState state, io.cortavyn.graph.NodeRuntime runtime) {
        @SuppressWarnings("unchecked") List<ChatMessage> messages = state.get(GRAPH_MESSAGES, List.class);
        int iteration = state.get(GRAPH_ITERATION, Integer.class);
        // Graph Interrupt is what makes a DeepInterrupt visible to the checkpoint runtime.
        return run(runtime.threadId(), messages, iteration, ignored -> { }).thenApply(run -> {
            StateUpdate update = new StateUpdate(Map.of(GRAPH_RUN, run));
            return run.interrupt() == null ? update : new Interrupt(update, Map.of("deepInterrupt", run.interrupt()));
        });
    }
    private CompletionStage<DeepRun> executeGraph(String threadId, List<ChatMessage> messages, int iteration) {
        return plan.graph().invoke(threadId, new GraphState(Map.of(GRAPH_MESSAGES, List.copyOf(messages), GRAPH_ITERATION, iteration))).thenCompose(result -> {
            if (result.status() == GraphStatus.FAILED) return CompletableFuture.failedStage(new IllegalStateException("deep graph failed: " + result.checkpointId()));
            DeepRun run = result.state().get(GRAPH_RUN, DeepRun.class);
            return CompletableFuture.completedFuture(new DeepRun(run.threadId(), run.conversation(), run.workspace(), run.todos(), run.interrupt(), result.status()));
        });
    }
    private CompletionStage<List<ChatMessage>> compactHistory(List<ChatMessage> messages) {
        int characters = messages.stream().mapToInt(message -> message.content().length()).sum();
        if (characters <= contextPolicy.historyCharacters()) return CompletableFuture.completedFuture(messages);
        String history = messages.stream().map(message -> message.role() + ": " + message.content()).collect(java.util.stream.Collectors.joining("\n"));
        // The summary request deliberately exposes no tools: summarising must not mutate the
        // workspace or create another approval while context is being reduced.
        ChatMessage request = new ChatMessage(ChatMessageRole.USER, "Summarize this agent history faithfully. Preserve goals, completed work, pending work, approvals, file paths and tool findings:\n" + history);
        return model.complete(new ChatRequest(List.of(request), List.of(), ChatGenerationParameters.defaults(), Map.of())).thenApply(response -> List.of(new ChatMessage(ChatMessageRole.SYSTEM, "Conversation summary:\n" + response.message().content())));
    }
    private CompletionStage<ChatMessage> resolve(String threadId, ToolCall call, ApprovalDecision decision, Consumer<DeepEvent> events) {
        if (!approvalPolicy.decisionsFor(call.name()).contains(decision.type())) return CompletableFuture.completedFuture(ChatMessage.toolResult(call.id(), "Approval decision is not allowed for tool: " + call.name()));
        return switch (decision.type()) { case APPROVE -> execute(threadId, call, events); case EDIT -> { if (decision.arguments() == null) yield CompletableFuture.completedFuture(ChatMessage.toolResult(call.id(), "Approval edit missing arguments")); ToolCall edited = new ToolCall(call.id(), call.name(), decision.arguments()); // Do not let an edited payload bypass the tool's public schema.
            String violation = validateArguments(threadId, edited); if (violation != null) yield CompletableFuture.completedFuture(ChatMessage.toolResult(call.id(), "Approval edit rejected: " + violation)); yield execute(threadId, edited, events); } case REJECT -> CompletableFuture.completedFuture(ChatMessage.toolResult(call.id(), decision.message() == null ? "Action rejected by reviewer." : decision.message())); case RESPOND -> CompletableFuture.completedFuture(ChatMessage.toolResult(call.id(), decision.message() == null ? "Reviewer response." : decision.message())); };
    }
    private CompletionStage<ChatMessage> execute(String threadId, ToolCall call, Consumer<DeepEvent> events) {
        events.accept(new DeepEvent.ToolCallRequested(call));
        for (ChatTool tool : allTools(threadId)) if (tool.definition().name().equals(call.name())) return tool.executor().execute(call, ToolRuntime.ephemeral(threadId)).handle((result, failure) -> failure == null ? compactToolResult(threadId, call, result, events) : ChatMessage.toolResult(call.id(), "Tool failed: " + failure.getMessage())).thenCompose(message -> emitToolResult(threadId, call, message, events));
        ChatMessage message = ChatMessage.toolResult(call.id(), "Unknown tool: " + call.name());
        return emitToolResult(threadId, call, message, events);
    }
    private CompletionStage<ChatMessage> emitToolResult(String threadId, ToolCall call, ChatMessage message, Consumer<DeepEvent> events) {
        events.accept(new DeepEvent.ToolResult(call, message));
        if ("start_task".equals(call.name())) events.accept(new DeepEvent.SubagentStarted(message.content(), String.valueOf(call.arguments().get("agent"))));
        if ("await_task".equals(call.name())) events.accept(new DeepEvent.SubagentCompleted(String.valueOf(call.arguments().get("taskId")), message.content()));
        if (!"write_todos".equals(call.name())) return CompletableFuture.completedFuture(message);
        return todoStore.read(threadId).thenApply(todos -> { events.accept(new DeepEvent.TodosUpdated(todos)); return message; });
    }
    private ChatMessage compactToolResult(String threadId, ToolCall call, ToolExecutionResult result, Consumer<DeepEvent> events) {
        if (result.content().length() <= contextPolicy.inlineToolResultCharacters()) return ChatMessage.toolResult(call.id(), result.contentBlocks(), result.error(), result.metadata());
        String path = "context/tool-results/" + call.id() + ".txt";
        // Keep the model context small while retaining the complete result for explicit reads.
        workspaceFor(threadId).write(path, result.content()).toCompletableFuture().join();
        events.accept(new DeepEvent.ContextOffloaded(path, result.content().length()));
        return ChatMessage.toolResult(call.id(), "Large tool result offloaded to " + path + "; use read_file to inspect it.");
    }
    private DeepWorkspace workspaceFor(String threadId) { return configuredWorkspace == null ? threadWorkspaces.computeIfAbsent(threadId, ignored -> new InMemoryWorkspace()) : configuredWorkspace; }
    private CompletionStage<Optional<WorkspaceSnapshot>> snapshotWorkspace(String threadId) { DeepWorkspace workspace = workspaceFor(threadId); if (workspace instanceof CheckpointableWorkspace checkpointable) return checkpointable.snapshot().thenApply(Optional::of); return CompletableFuture.completedFuture(Optional.empty()); }
    private CompletionStage<Void> restoreWorkspace(String threadId, @org.jspecify.annotations.Nullable WorkspaceSnapshot snapshot) { if (snapshot == null) return CompletableFuture.completedFuture(null); DeepWorkspace workspace = workspaceFor(threadId); return workspace instanceof CheckpointableWorkspace checkpointable ? checkpointable.restore(snapshot) : CompletableFuture.failedStage(new IllegalStateException("workspace cannot restore a durable checkpoint")); }
    private @org.jspecify.annotations.Nullable String validateArguments(String threadId, ToolCall call) {
        for (ChatTool tool : allTools(threadId)) if (tool.definition().name().equals(call.name())) return validate(call.arguments(), tool.definition().inputSchema(), "arguments");
        return "unknown tool: " + call.name();
    }
    @SuppressWarnings("unchecked")
    private static @org.jspecify.annotations.Nullable String validate(Object value, Map<String, Object> schema, String path) {
        Object type = schema.get("type");
        if ("object".equals(type)) {
            if (!(value instanceof Map<?, ?> object)) return path + " must be an object";
            Object required = schema.get("required");
            if (required instanceof Iterable<?> fields) for (Object field : fields) if (!object.containsKey(String.valueOf(field))) return path + "." + field + " is required";
            Object properties = schema.get("properties");
            if (properties instanceof Map<?, ?> fields) for (Map.Entry<?, ?> field : fields.entrySet()) if (object.containsKey(field.getKey()) && field.getValue() instanceof Map<?, ?> child) { String error = validate(object.get(field.getKey()), (Map<String, Object>) child, path + "." + field.getKey()); if (error != null) return error; }
        } else if ("array".equals(type)) {
            if (!(value instanceof Iterable<?> values)) return path + " must be an array";
            if (schema.get("items") instanceof Map<?, ?> items) for (Object item : values) { String error = validate(item, (Map<String, Object>) items, path + "[]"); if (error != null) return error; }
        } else if ("string".equals(type) && !(value instanceof String)) return path + " must be a string";
        else if ("boolean".equals(type) && !(value instanceof Boolean)) return path + " must be a boolean";
        else if ("integer".equals(type) && (!(value instanceof Number number) || Math.rint(number.doubleValue()) != number.doubleValue())) return path + " must be an integer";
        else if ("number".equals(type) && !(value instanceof Number)) return path + " must be a number";
        if (schema.get("enum") instanceof Iterable<?> choices) { boolean found = false; for (Object choice : choices) if (Objects.equals(choice, value)) { found = true; break; } if (!found) return path + " is not an allowed value"; }
        return null;
    }
    private ChatTool[] allTools(String threadId) {
        List<ChatTool> result = new ArrayList<>(tools);
        result.addAll(DeepTools.workspace(workspaceFor(threadId)));
        result.add(DeepTools.todos(todoStore));
        result.addAll(DeepTools.skills(skills));
        result.addAll(DeepTools.memory(memory, memoryNamespace));
        if (sandbox != null) result.addAll(DeepTools.sandbox(sandbox));
        result.addAll(DeepTools.subagents(!subagents.isEmpty(), subagentRegistry));
        mcpSources.forEach(source -> result.addAll(source.tools()));
        result.addAll(DeepTools.mcpResources(mcpSources));
        return result.toArray(ChatTool[]::new);
    }
    public static final class Builder {
        private final ChatModel model;
        private List<ChatTool> tools = List.of();
        private String systemPrompt = "";
        private @org.jspecify.annotations.Nullable DeepWorkspace workspace;
        private ContextPolicy contextPolicy = ContextPolicy.defaults();
        private DeepMemory memory = DeepMemory.none();
        private String memoryNamespace = "default";
        private List<DeepSkill> skills = List.of();
        private List<DeepSubagent> subagents = List.of();
        private ApprovalPolicy approvalPolicy = ApprovalPolicy.writesAndExecute();
        private DeepRunStore runStore = DeepRunStore.inMemory();
        private List<McpToolSource> mcpSources = List.of();
        private DeepTodoStore todoStore = DeepTodoStore.inMemory();
        private @org.jspecify.annotations.Nullable Sandbox sandbox;
        private DeepTaskStore taskStore = DeepTaskStore.inMemory();
        private Builder(ChatModel model) { this.model = Objects.requireNonNull(model, "model must not be null"); }
        public Builder tools(ChatTool... value) { tools = List.of(value); return this; }
        public Builder systemPrompt(String value) { systemPrompt = Objects.requireNonNull(value, "systemPrompt must not be null"); return this; }
        public Builder workspace(DeepWorkspace value) { workspace = Objects.requireNonNull(value, "workspace must not be null"); return this; }
        public Builder contextPolicy(ContextPolicy value) { contextPolicy = Objects.requireNonNull(value, "contextPolicy must not be null"); return this; }
        /** Configures caller-scoped, persistent instructions. */
        public Builder memory(DeepMemory value, String namespace) { memory = Objects.requireNonNull(value, "memory must not be null"); if (namespace == null || namespace.isBlank()) throw new IllegalArgumentException("memory namespace must not be blank"); memoryNamespace = namespace; return this; }
        /** Registers skills; only their metadata is placed in the starting context. */
        public Builder skills(DeepSkill... value) { skills = List.of(value); return this; }
        /** Registers named specialists exposed through task, start_task, and await_task. */
        public Builder subagents(DeepSubagent... value) { subagents = List.of(value); return this; }
        /** Requires explicit review for configured tool calls; file writes are protected by default. */
        public Builder approvalPolicy(ApprovalPolicy value) { approvalPolicy = Objects.requireNonNull(value, "approvalPolicy must not be null"); return this; }
        /** Stores paused approvals; inject an application bridge to durable graph checkpoints in production. */
        public Builder runStore(DeepRunStore value) { runStore = Objects.requireNonNull(value, "runStore must not be null"); return this; }
        /** Adds application-owned MCP tools; transports and credentials remain outside the harness. */
        public Builder mcpSources(McpToolSource... value) { mcpSources = List.of(value); return this; }
        public Builder todoStore(DeepTodoStore value) { todoStore = Objects.requireNonNull(value, "todoStore must not be null"); return this; }
        /** Enables the execute tool through an application-provided isolated execution backend. */
        public Builder sandbox(Sandbox value) { sandbox = Objects.requireNonNull(value, "sandbox must not be null"); return this; }
        public Builder taskStore(DeepTaskStore value) { taskStore = Objects.requireNonNull(value, "taskStore must not be null"); return this; }
        public DeepAgent build() { return new DeepAgent(this); }
    }
}
