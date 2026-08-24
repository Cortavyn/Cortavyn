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
import io.cortavyn.model.api.ChatCompletion;
import io.cortavyn.model.api.ChatContent;
import io.cortavyn.model.api.ChatResponse;
import io.cortavyn.model.api.ChatStreamEvent;
import io.cortavyn.model.api.ChatTextDelta;
import io.cortavyn.model.api.StreamingChatModel;
import io.cortavyn.model.api.ToolCall;
import io.cortavyn.model.api.ToolDefinition;
import java.nio.file.Path;
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
import java.util.concurrent.Flow;
import java.util.function.Consumer;

/** Durable, tool-using agent harness with a virtual workspace and task planning. */
public final class DeepAgent implements AutoCloseable {
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
    private final String agentInstructions;
    private final ConcurrentMap<String, DeepAgent> subagents;
    private final SubagentRegistry subagentRegistry;
    private final ApprovalPolicy approvalPolicy;
    private final DeepRunStore runStore;
    private final List<McpToolSource> mcpSources;
    private final DeepTodoStore todoStore;
    private final @org.jspecify.annotations.Nullable Sandbox sandbox;
    private final @org.jspecify.annotations.Nullable DeepInterpreter interpreter;
    private final DeepHarnessProfile harnessProfile;
    private final PromptCachePolicy promptCachePolicy;
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
        agentInstructions = builder.agentInstructions;
        ConcurrentMap<String, DeepAgent> configuredSubagents = new ConcurrentHashMap<>();
        for (DeepSubagent subagent : builder.subagents) {
            // A specialist gets a fresh agent/context. A configured parent workspace may be
            // wrapped so a specialist can only see the paths delegated to it.
            Builder child = DeepAgent.builder(model).systemPrompt(subagent.systemPrompt()).tools(subagent.tools().toArray(ChatTool[]::new)).contextPolicy(contextPolicy).approvalPolicy(subagent.approvalPolicy() == null ? builder.approvalPolicy : subagent.approvalPolicy()).generalPurposeSubagent(false);
            if (builder.interpreter != null) child.interpreter(builder.interpreter);
            if (configuredWorkspace != null) child.workspace(subagent.workspacePermissions().isEmpty() ? configuredWorkspace : new PermissionedWorkspace(configuredWorkspace, subagent.workspacePermissions()));
            configuredSubagents.put(subagent.name(), child.build());
        }
        if (builder.generalPurposeSubagent) configuredSubagents.put("general-purpose", DeepAgent.builder(model).systemPrompt("You are a general-purpose delegated worker. Complete the assigned task and report concise findings.").tools(builder.tools.toArray(ChatTool[]::new)).contextPolicy(contextPolicy).approvalPolicy(builder.approvalPolicy).generalPurposeSubagent(false).build());
        subagents = configuredSubagents;
        approvalPolicy = builder.approvalPolicy;
        runStore = builder.runStore;
        mcpSources = List.copyOf(builder.mcpSources);
        todoStore = builder.todoStore;
        sandbox = builder.sandbox;
        interpreter = builder.interpreter;
        harnessProfile = builder.harnessProfile;
        promptCachePolicy = builder.promptCachePolicy;
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
    /** Releases thread-scoped interpreter sessions and their runtime resources. */
    @Override public void close() { if (interpreter != null) interpreter.close(); }
    /** Returns durable checkpoints created by this agent's internal graph for one thread. */
    public List<Checkpoint> history(String threadId) { return plan.graph().history(threadId); }
    public CompletionStage<DeepRun> invoke(String threadId, String input) {
        Objects.requireNonNull(input, "input must not be null");
        return start(threadId, List.of(new io.cortavyn.model.api.TextContent(input)), ignored -> { }, true);
    }
    private CompletionStage<DeepRun> start(String threadId, List<ChatContent> input, Consumer<DeepEvent> events, boolean graphDriven) {
        return memory.load(memoryNamespace).thenCompose(loadedMemory -> {
        // Build the initial context once. Skill instructions are intentionally deferred; only
        // their catalogue metadata enters the prompt until the model calls load_skill.
        List<ChatMessage> initial = new ArrayList<>();
        if (!systemPrompt.isBlank()) initial.add(new ChatMessage(ChatMessageRole.SYSTEM, systemPrompt));
        if (!loadedMemory.isBlank()) initial.add(new ChatMessage(ChatMessageRole.SYSTEM, "Persistent memory:\n" + loadedMemory));
        if (!skills.isEmpty()) initial.add(new ChatMessage(ChatMessageRole.SYSTEM, "Available skills (load their instructions when relevant):\n" + skills.stream().map(skill -> "- " + skill.name() + ": " + skill.description()).collect(java.util.stream.Collectors.joining("\n"))));
        if (!agentInstructions.isBlank()) initial.add(new ChatMessage(ChatMessageRole.SYSTEM, agentInstructions));
        initial.add(new ChatMessage(ChatMessageRole.SYSTEM, "Use write_todos for multi-step work. Use the workspace tools for large intermediate results and verify changes."));
        initial.add(new ChatMessage(ChatMessageRole.USER, input));
        // The stream needs per-step events, so it drives the same loop directly. Invoke uses the
        // graph wrapper to persist graph checkpoints and convert approvals into interrupts.
        return graphDriven ? executeGraph(threadId, initial, 0) : run(threadId, initial, 0, events);
        });
    }
    public CompletionStage<DeepRun> invoke(DeepRequest request) { return start(request.threadId(), request.input(), ignored -> { }, true); }
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
    /** Continues an approved run while emitting progress and streamed model text. */
    public java.util.concurrent.Flow.Publisher<DeepEvent> resumeStream(String threadId, List<ApprovalDecision> decisions) {
        return subscriber -> {
            java.util.concurrent.SubmissionPublisher<DeepEvent> publisher = new java.util.concurrent.SubmissionPublisher<>();
            publisher.subscribe(subscriber);
            resume(threadId, decisions, publisher::submit, false).whenComplete((run, failure) -> {
                if (failure != null) publisher.submit(new DeepEvent.Failed(failure));
                else publisher.submit(run.interrupt() == null ? new DeepEvent.Completed(run) : new DeepEvent.Interrupted(run));
                publisher.close();
            });
        };
    }
    /** Continues a paused run after one decision per pending action, in request order. */
    public CompletionStage<DeepRun> resume(String threadId, List<ApprovalDecision> decisions) {
        return resume(threadId, decisions, ignored -> { }, true);
    }
    private CompletionStage<DeepRun> resume(String threadId, List<ApprovalDecision> decisions, Consumer<DeepEvent> events, boolean graphDriven) {
        return runStore.get(threadId).thenCompose(found -> {
        DeepPendingRun state = found.orElseThrow(() -> new IllegalArgumentException("no pending approval for thread: " + threadId));
        if (decisions.size() != state.calls().size()) return CompletableFuture.failedStage(new IllegalArgumentException("one decision is required for each pending action"));
        // Restore the virtual files before applying approved calls. This lets a new JVM continue
        // an approval run whose previous agent instance no longer exists.
        return restoreWorkspace(threadId, state.workspaceSnapshot()).thenCompose(restored -> {
            List<ChatMessage> messages = new ArrayList<>(state.messages());
            List<CompletableFuture<ChatMessage>> results = new ArrayList<>();
            for (int index = 0; index < state.calls().size(); index++) results.add(resolve(threadId, state.calls().get(index), decisions.get(index), ignored -> { }).toCompletableFuture());
            return CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).thenCompose(ignored -> {
                results.forEach(result -> messages.add(result.join()));
                return runStore.delete(threadId).thenCompose(deleted -> graphDriven
                        ? executeGraph(threadId, messages, state.iteration() + 1)
                        : run(threadId, messages, state.iteration() + 1, events));
            });
        });
        });
    }
    private CompletionStage<DeepRun> run(String threadId, List<ChatMessage> messages, int iteration, Consumer<DeepEvent> events) {
        if (iteration >= contextPolicy.maxIterations()) return CompletableFuture.failedStage(new IllegalStateException("deep agent exceeded maxIterations: " + contextPolicy.maxIterations()));
        ChatTool[] available = allTools(threadId); List<ToolDefinition> definitions = java.util.Arrays.stream(available).map(ChatTool::definition).toList();
        // Compact before every model turn: tool results can otherwise grow the conversation far
        // beyond a provider context window during long-running tasks.
        return compactHistory(messages).thenCompose(activeMessages -> complete(new ChatRequest(activeMessages, definitions, ChatGenerationParameters.defaults(), promptCachePolicy.enabled() ? Map.of("cortavyn.promptCache", true) : Map.of()), events).thenCompose(response -> {
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

    private CompletionStage<ChatResponse> complete(ChatRequest request, Consumer<DeepEvent> events) {
        if (!(model instanceof StreamingChatModel streamingModel)) {
            return model.complete(request);
        }
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        streamingModel.stream(request).subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ChatStreamEvent event) {
                if (event instanceof ChatTextDelta delta) {
                    events.accept(new DeepEvent.TextDelta(delta.text()));
                } else if (event instanceof ChatCompletion completion) {
                    response.complete(completion.response());
                }
            }
            @Override public void onError(Throwable failure) { response.completeExceptionally(failure); }
            @Override public void onComplete() {
                if (!response.isDone()) {
                    response.completeExceptionally(new IllegalStateException("model stream completed without a ChatCompletion"));
                }
            }
        });
        return response;
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
        int tokens = messages.stream().mapToInt(DeepAgent::estimatedTokens).sum();
        if (characters <= contextPolicy.historyCharacters() && tokens <= contextPolicy.effectiveHistoryTokens()) return CompletableFuture.completedFuture(messages);
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
        if (result.content().length() <= contextPolicy.inlineToolResultCharacters() && estimatedTokens(result.content()) <= contextPolicy.inlineToolResultTokens()) return ChatMessage.toolResult(call.id(), result.contentBlocks(), result.error(), result.metadata());
        String path = "context/tool-results/" + call.id() + ".txt";
        // Keep the model context small while retaining the complete result for explicit reads.
        workspaceFor(threadId).write(path, result.content()).toCompletableFuture().join();
        events.accept(new DeepEvent.ContextOffloaded(path, result.content().length()));
        String preview = result.content().substring(0, Math.min(result.content().length(), contextPolicy.offloadPreviewCharacters()));
        return ChatMessage.toolResult(call.id(), "Large tool result offloaded to " + path + "; preview:\n" + preview + (preview.length() < result.content().length() ? "\n[truncated; use read_file for the complete result]" : ""));
    }
    private static int estimatedTokens(ChatMessage message) { return estimatedTokens(message.content()) + message.contentBlocks().stream().filter(block -> !(block instanceof io.cortavyn.model.api.TextContent)).mapToInt(ignored -> 256).sum(); }
    private static int estimatedTokens(String text) { return Math.max(1, (text.length() + 3) / 4); }
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
        if (harnessProfile.enables(DeepHarnessProfile.BuiltIn.WORKSPACE)) result.addAll(DeepTools.workspace(workspaceFor(threadId)));
        if (harnessProfile.enables(DeepHarnessProfile.BuiltIn.TODOS)) result.add(DeepTools.todos(todoStore));
        if (harnessProfile.enables(DeepHarnessProfile.BuiltIn.SKILLS)) result.addAll(DeepTools.skills(skills));
        if (harnessProfile.enables(DeepHarnessProfile.BuiltIn.MEMORY)) result.addAll(DeepTools.memory(memory, memoryNamespace));
        if (sandbox != null && harnessProfile.enables(DeepHarnessProfile.BuiltIn.SANDBOX)) result.addAll(DeepTools.sandbox(sandbox));
        if (interpreter != null && harnessProfile.enables(DeepHarnessProfile.BuiltIn.INTERPRETER)) result.add(DeepTools.interpreter(interpreter));
        if (harnessProfile.enables(DeepHarnessProfile.BuiltIn.SUBAGENTS)) { result.addAll(DeepTools.subagents(!subagents.isEmpty(), subagentRegistry)); result.add(DeepTools.defineSpecialist(this::registerSpecialist)); }
        mcpSources.forEach(source -> result.addAll(source.tools()));
        if (harnessProfile.enables(DeepHarnessProfile.BuiltIn.MCP_RESOURCES)) result.addAll(DeepTools.mcpResources(mcpSources));
        return result.toArray(ChatTool[]::new);
    }
    private void registerSpecialist(DynamicSpecialist specialist) { subagents.putIfAbsent(specialist.name(), DeepAgent.builder(model).systemPrompt(specialist.systemPrompt()).tools(tools.toArray(ChatTool[]::new)).contextPolicy(contextPolicy).approvalPolicy(approvalPolicy).generalPurposeSubagent(false).build()); }
    public static final class Builder {
        private final ChatModel model;
        private List<ChatTool> tools = List.of();
        private String systemPrompt = "";
        private @org.jspecify.annotations.Nullable DeepWorkspace workspace;
        private ContextPolicy contextPolicy = ContextPolicy.defaults();
        private DeepMemory memory = DeepMemory.none();
        private String memoryNamespace = "default";
        private List<DeepSkill> skills = List.of();
        private String agentInstructions = "";
        private List<DeepSubagent> subagents = List.of();
        private ApprovalPolicy approvalPolicy = ApprovalPolicy.writesAndExecute();
        private DeepRunStore runStore = DeepRunStore.inMemory();
        private List<McpToolSource> mcpSources = List.of();
        private DeepTodoStore todoStore = DeepTodoStore.inMemory();
        private @org.jspecify.annotations.Nullable Sandbox sandbox;
        private @org.jspecify.annotations.Nullable DeepInterpreter interpreter;
        private DeepHarnessProfile harnessProfile = DeepHarnessProfile.defaults();
        private DeepTaskStore taskStore = DeepTaskStore.inMemory();
        private boolean generalPurposeSubagent = true;
        private PromptCachePolicy promptCachePolicy = PromptCachePolicy.defaults();
        private Builder(ChatModel model) { this.model = Objects.requireNonNull(model, "model must not be null"); }
        public Builder tools(ChatTool... value) { tools = List.of(value); return this; }
        public Builder systemPrompt(String value) { systemPrompt = Objects.requireNonNull(value, "systemPrompt must not be null"); return this; }
        public Builder workspace(DeepWorkspace value) { workspace = Objects.requireNonNull(value, "workspace must not be null"); return this; }
        public Builder contextPolicy(ContextPolicy value) { contextPolicy = Objects.requireNonNull(value, "contextPolicy must not be null"); return this; }
        /** Configures caller-scoped, persistent instructions. */
        public Builder memory(DeepMemory value, String namespace) { memory = Objects.requireNonNull(value, "memory must not be null"); if (namespace == null || namespace.isBlank()) throw new IllegalArgumentException("memory namespace must not be blank"); memoryNamespace = namespace; return this; }
        /** Registers skills; only their metadata is placed in the starting context. */
        public Builder skills(DeepSkill... value) { skills = List.of(value); return this; }
        /** Discovers SKILL.md catalogues and AGENTS.md repository instructions below a root. */
        public Builder discoverSkills(Path root) { try { skills = SkillCatalog.load(root); agentInstructions = SkillCatalog.loadAgentInstructions(root); return this; } catch (java.io.IOException failure) { throw new IllegalArgumentException("could not discover agent skills", failure); } }
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
        /** Enables isolated JavaScript evaluation with the supplied interpreter. */
        public Builder interpreter(DeepInterpreter value) { interpreter = Objects.requireNonNull(value, "interpreter must not be null"); return this; }
        /** Selects built-ins suitable for a concrete model/deployment. Application tools remain available. */
        public Builder harnessProfile(DeepHarnessProfile value) { harnessProfile = Objects.requireNonNull(value, "harnessProfile must not be null"); return this; }
        public Builder taskStore(DeepTaskStore value) { taskStore = Objects.requireNonNull(value, "taskStore must not be null"); return this; }
        /** Enables the default general-purpose delegated worker (enabled by default). */
        public Builder generalPurposeSubagent(boolean value) { generalPurposeSubagent = value; return this; }
        /** Emits provider-specific cache markers for static system, memory, and skill context. */
        public Builder promptCachePolicy(PromptCachePolicy value) { promptCachePolicy = Objects.requireNonNull(value, "promptCachePolicy must not be null"); return this; }
        public DeepAgent build() { return new DeepAgent(this); }
    }
}
