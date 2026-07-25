package io.cortavyn.graph;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SubmissionPublisher;
import org.jspecify.annotations.Nullable;

/** Immutable executable graph with durable, parallel superstep scheduling. */
public final class CompiledGraph<S> {
    private final StateSchema<S> schema;
    private final Map<String, GraphNode<S>> nodes;
    private final Map<String, List<String>> edges;
    private final Map<String, ConditionalEdge<S>> conditional;
    private final Map<String, RetryPolicy> retries;
    private final Set<String> interruptBefore;
    private final Set<String> interruptAfter;
    private final GraphOptions options;

    CompiledGraph(StateSchema<S> schema, Map<String, GraphNode<S>> nodes, Map<String, List<String>> edges, Map<String, ConditionalEdge<S>> conditional, Map<String, RetryPolicy> retries, Set<String> interruptBefore, Set<String> interruptAfter, GraphOptions options) {
        this.schema = schema; this.nodes = Map.copyOf(nodes); this.edges = copy(edges); this.conditional = Map.copyOf(conditional); this.retries = Map.copyOf(retries); this.interruptBefore = Set.copyOf(interruptBefore); this.interruptAfter = Set.copyOf(interruptAfter); this.options = options;
    }
    public CompletionStage<RunResult<S>> invoke(String threadId, S initialState) { return start(threadId, initialState, tasks(edges.getOrDefault(StateGraph.START, List.of())), null).completion(); }
    public GraphRun<S> stream(String threadId, S initialState) { return start(threadId, initialState, tasks(edges.getOrDefault(StateGraph.START, List.of())), null); }
    public GraphRun<S> stream(String threadId, S initialState, Set<StreamMode> modes) {
        GraphRun<S> run = stream(threadId, initialState);
        return new GraphRun<>(run.completion(), subscriber -> run.events().subscribe(new FilteringSubscriber(subscriber, Set.copyOf(modes))));
    }
    public CompletionStage<RunResult<S>> resume(ResumeToken token, StateUpdate update) {
        Checkpoint checkpoint = options.checkpoints().get(token.checkpointId()).orElseThrow(() -> new IllegalArgumentException("unknown checkpoint: " + token.checkpointId()));
        if (checkpoint.status() != GraphStatus.INTERRUPTED) throw new IllegalStateException("checkpoint is not interrupted: " + checkpoint.id());
        return start(checkpoint.threadId(), schema.merge(fromValues(checkpoint.state()), update), checkpoint.nextTasks(), checkpoint.id()).completion();
    }
    public CompletionStage<RunResult<S>> fork(String checkpointId) {
        Checkpoint checkpoint = options.checkpoints().get(checkpointId).orElseThrow(() -> new IllegalArgumentException("unknown checkpoint: " + checkpointId));
        return start(checkpoint.threadId(), fromValues(checkpoint.state()), checkpoint.nextTasks(), checkpoint.id()).completion();
    }
    public List<Checkpoint> history(String threadId) { return options.checkpoints().history(threadId); }
    public String toMermaid() { StringBuilder value = new StringBuilder("flowchart TD\n"); edges.forEach((from, tos) -> tos.forEach(to -> value.append("    ").append(id(from)).append(" --> ").append(id(to)).append('\n'))); conditional.keySet().forEach(from -> value.append("    ").append(id(from)).append(" -. conditional .-> route\n")); return value.toString(); }

    private GraphRun<S> start(String threadId, S state, List<CheckpointTask> next, @Nullable String parent) {
        if (threadId == null || threadId.isBlank()) throw new IllegalArgumentException("threadId must not be blank");
        // A run owns a hot event stream; consumers may observe state, debug, and checkpoint events.
        SubmissionPublisher<GraphEvent> publisher = new SubmissionPublisher<>(options.executor(), Flow.defaultBufferSize());
        CompletableFuture<RunResult<S>> completion = CompletableFuture.supplyAsync(() -> execute(threadId, UUID.randomUUID().toString(), state, next, parent, publisher), options.executor());
        completion = completion.whenComplete((ignored, failure) -> publisher.close());
        return new GraphRun<>(completion, publisher);
    }
    private RunResult<S> execute(String threadId, String runId, S initial, List<CheckpointTask> first, @Nullable String parentCheckpoint, SubmissionPublisher<GraphEvent> events) {
        S state = initial; List<CheckpointTask> active = new ArrayList<>(first); @Nullable String parent = parentCheckpoint;
        try {
            for (int step = 0; step < options.recursionLimit(); step++) {
                // A superstep starts with every task that became ready in the prior step.
                active.removeIf(task -> StateGraph.END.equals(task.nodeId()));
                if (active.isEmpty()) return checkpoint(threadId, runId, parent, GraphStatus.SUCCEEDED, state, List.of(), null, null, events);
                // Stable scheduling makes reducer application reproducible despite asynchronous work.
                active = active.stream().sorted(Comparator.comparing(CheckpointTask::nodeId).thenComparing(task -> task.input().toString())).toList();
                String before = parent == null ? active.stream().map(CheckpointTask::nodeId).filter(interruptBefore::contains).findFirst().orElse(null) : null;
                if (before != null) return checkpoint(threadId, runId, parent, GraphStatus.INTERRUPTED, state, active, Map.of("node", before, "when", "before"), null, events);
                List<NodeExecution> completed = executeStep(threadId, runId, state, active, events);
                // Send payloads are deliberately not merged globally: they belong only to their target task.
                for (NodeExecution execution : completed) if (!(execution.result() instanceof Send) && !(execution.result() instanceof Sends)) state = schema.merge(state, execution.result().update());
                events.submit(new GraphEvent(GraphEventType.VALUE, null, schema.values(state)));
                NodeExecution interrupted = completed.stream().filter(execution -> execution.result() instanceof Interrupt).findFirst().orElse(null);
                if (interrupted != null) return checkpoint(threadId, runId, parent, GraphStatus.INTERRUPTED, state, active, ((Interrupt) interrupted.result()).payload(), null, events);
                List<CheckpointTask> following = nextTasks(state, completed);
                String after = completed.stream().map(execution -> execution.task().nodeId()).filter(interruptAfter::contains).findFirst().orElse(null);
                if (after != null) return checkpoint(threadId, runId, parent, GraphStatus.INTERRUPTED, state, following, Map.of("node", after, "when", "after"), null, events);
                // Persist after every completed superstep so an interrupted process can resume safely.
                RunResult<S> checkpoint = checkpoint(threadId, runId, parent, GraphStatus.SUCCEEDED, state, following, null, null, events);
                parent = checkpoint.checkpointId(); active = new ArrayList<>(following); state = schema.clearEphemeral(state);
            }
            return checkpoint(threadId, runId, parent, GraphStatus.FAILED, state, active, null, "recursion limit exceeded", events);
        } catch (RuntimeException failure) {
            events.submit(new GraphEvent(GraphEventType.FAILED, null, Map.of("message", String.valueOf(failure.getMessage()))));
            return checkpoint(threadId, runId, parent, GraphStatus.FAILED, state, active, null, String.valueOf(failure.getMessage()), events);
        }
    }
    private List<NodeExecution> executeStep(String threadId, String runId, S state, List<CheckpointTask> active, SubmissionPublisher<GraphEvent> events) {
        // The semaphore constrains fan-out while each task still receives its own input overlay.
        Semaphore permits = new Semaphore(options.maxConcurrency());
        List<CompletableFuture<NodeExecution>> futures = active.stream().map(task -> CompletableFuture.supplyAsync(() -> { permits.acquireUninterruptibly(); try { return callNode(threadId, runId, schema.merge(state, new StateUpdate(task.input())), task, events, 1); } finally { permits.release(); } }, options.executor())).toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        return futures.stream().map(CompletableFuture::join).sorted(Comparator.comparing(execution -> execution.task().nodeId())).toList();
    }
    private NodeExecution callNode(String threadId, String runId, S state, CheckpointTask task, SubmissionPublisher<GraphEvent> events, int attempt) {
        String node = task.nodeId(); events.submit(new GraphEvent(GraphEventType.NODE_STARTED, node, Map.of("attempt", attempt)));
        try {
            GraphNode<S> graphNode = Objects.requireNonNull(nodes.get(node), "unknown node: " + node);
            NodeResult result = Objects.requireNonNull(graphNode.execute(state, new NodeRuntime(threadId, runId, node, attempt, events::submit)).toCompletableFuture().join(), "node result must not be null");
            events.submit(new GraphEvent(GraphEventType.UPDATE, node, result.update().values())); events.submit(new GraphEvent(GraphEventType.NODE_COMPLETED, node, result.update().values()));
            return new NodeExecution(task, result);
        } catch (RuntimeException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause(); RetryPolicy policy = retries.get(node);
            if (policy != null && attempt < policy.maxAttempts() && policy.retryOn().test(cause)) {
                events.submit(new GraphEvent(GraphEventType.RETRY, node, Map.of("attempt", attempt, "message", String.valueOf(cause.getMessage()))));
                if (!policy.delay().isZero()) try { Thread.sleep(policy.delay()); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                return callNode(threadId, runId, state, task, events, attempt + 1);
            }
            throw new IllegalStateException("node failed: " + node, cause);
        }
    }
    private List<CheckpointTask> nextTasks(S state, List<NodeExecution> executions) {
        List<CheckpointTask> next = new ArrayList<>();
        for (NodeExecution execution : executions) {
            NodeResult result = execution.result();
            if (result instanceof Command command) next.addAll(tasks(command.gotoNodes()));
            // A Send retains its update as task-local input; multiple sends may target the same node.
            else if (result instanceof Send send) next.add(new CheckpointTask(send.target(), send.update().values()));
            else if (result instanceof Sends sends) sends.sends().forEach(send -> next.add(new CheckpointTask(send.target(), send.update().values())));
            else { next.addAll(tasks(edges.getOrDefault(execution.task().nodeId(), List.of()))); ConditionalEdge<S> router = conditional.get(execution.task().nodeId()); if (router != null) next.addAll(tasks(router.route(state))); }
        }
        next.forEach(task -> { if (!StateGraph.END.equals(task.nodeId()) && !nodes.containsKey(task.nodeId())) throw new IllegalStateException("node selected unknown target: " + task.nodeId()); });
        return List.copyOf(next);
    }
    private RunResult<S> checkpoint(String threadId, String runId, @Nullable String parent, GraphStatus status, S state, List<CheckpointTask> next, @Nullable Map<String, Object> interrupt, @Nullable String failure, SubmissionPublisher<GraphEvent> events) {
        String id = UUID.randomUUID().toString(); options.checkpoints().save(new Checkpoint(id, threadId, runId, parent, status, Instant.now(), schema.values(state), next, interrupt, failure));
        events.submit(new GraphEvent(GraphEventType.CHECKPOINT, null, Map.of("checkpointId", id, "status", status.name())));
        ResumeToken token = status == GraphStatus.INTERRUPTED ? new ResumeToken(id) : null; if (token != null) events.submit(new GraphEvent(GraphEventType.INTERRUPTED, null, interrupt == null ? Map.of() : interrupt));
        return new RunResult<>(threadId, runId, status, state, id, token);
    }
    private S fromValues(Map<String, Object> values) { return schema.merge(schema.empty(), new StateUpdate(values)); }
    private static List<CheckpointTask> tasks(List<String> nodes) { return nodes.stream().map(CheckpointTask::of).toList(); }
    private static String id(String value) { return value.replaceAll("[^A-Za-z0-9_]", "_"); }
    private static <T> Map<String, List<T>> copy(Map<String, List<T>> source) { Map<String, List<T>> result = new LinkedHashMap<>(); source.forEach((key, value) -> result.put(key, List.copyOf(value))); return Map.copyOf(result); }
    private record NodeExecution(CheckpointTask task, NodeResult result) { }
    private record FilteringSubscriber(Flow.Subscriber<? super GraphEvent> target, Set<StreamMode> modes) implements Flow.Subscriber<GraphEvent> {
        @Override public void onSubscribe(Flow.Subscription subscription) { target.onSubscribe(subscription); }
        @Override public void onNext(GraphEvent event) { if (modes.stream().anyMatch(mode -> mode.accepts(event.type()))) target.onNext(event); }
        @Override public void onError(Throwable throwable) { target.onError(throwable); }
        @Override public void onComplete() { target.onComplete(); }
    }
}
