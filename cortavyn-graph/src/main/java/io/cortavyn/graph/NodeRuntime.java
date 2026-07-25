package io.cortavyn.graph;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Run metadata and custom-event sink visible to a node. */
public final class NodeRuntime {
    private final String threadId;
    private final String runId;
    private final String nodeId;
    private final int attempt;
    private final Consumer<GraphEvent> events;
    NodeRuntime(String threadId, String runId, String nodeId, int attempt, Consumer<GraphEvent> events) {
        this.threadId = Objects.requireNonNull(threadId, "threadId must not be null"); this.runId = Objects.requireNonNull(runId, "runId must not be null");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null"); this.attempt = attempt; this.events = Objects.requireNonNull(events, "events must not be null");
    }
    public String threadId() { return threadId; }
    public String runId() { return runId; }
    public String nodeId() { return nodeId; }
    public int attempt() { return attempt; }
    public void emit(Map<String, Object> event) { emit(GraphEventType.CUSTOM, event); }
    public void emit(GraphEventType type, Map<String, Object> event) { events.accept(new GraphEvent(type, nodeId, event)); }
}
