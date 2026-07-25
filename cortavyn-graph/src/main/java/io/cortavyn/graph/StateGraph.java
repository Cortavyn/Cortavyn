package io.cortavyn.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Declarative builder for a stateful graph. Inspired by LangGraph's Graph API. */
public final class StateGraph<S> {
    public static final String START = "__start__";
    public static final String END = "__end__";
    private final StateSchema<S> schema;
    private final Map<String, GraphNode<S>> nodes = new LinkedHashMap<>();
    private final Map<String, List<String>> edges = new LinkedHashMap<>();
    private final Map<String, ConditionalEdge<S>> conditionalEdges = new LinkedHashMap<>();
    private final Map<String, RetryPolicy> retries = new LinkedHashMap<>();
    private final Set<String> interruptBefore = new LinkedHashSet<>();
    private final Set<String> interruptAfter = new LinkedHashSet<>();

    public StateGraph(StateSchema<S> schema) { this.schema = Objects.requireNonNull(schema, "schema must not be null"); }
    public StateGraph<S> addNode(String id, GraphNode<S> node) { return addNode(id, node, null); }
    public StateGraph<S> addNode(String id, GraphNode<S> node, @Nullable RetryPolicy retryPolicy) {
        validId(id); if (nodes.putIfAbsent(id, Objects.requireNonNull(node, "node must not be null")) != null) throw new IllegalArgumentException("duplicate node: " + id);
        if (retryPolicy != null) retries.put(id, retryPolicy); return this;
    }
    public StateGraph<S> addEdge(String from, String to) { validEndpoint(from); validEndpoint(to); edges.computeIfAbsent(from, ignored -> new ArrayList<>()).add(to); return this; }
    public StateGraph<S> addConditionalEdges(String from, ConditionalEdge<S> router) { validId(from); if (conditionalEdges.putIfAbsent(from, Objects.requireNonNull(router, "router must not be null")) != null) throw new IllegalArgumentException("conditional edge already registered: " + from); return this; }
    public StateGraph<S> interruptBefore(String nodeId) { validId(nodeId); interruptBefore.add(nodeId); return this; }
    public StateGraph<S> interruptAfter(String nodeId) { validId(nodeId); interruptAfter.add(nodeId); return this; }
    public CompiledGraph<S> compile() { return compile(GraphOptions.defaults()); }
    public CompiledGraph<S> compile(GraphOptions options) {
        // Validate static topology up front; dynamic Command and Send targets remain checked at runtime.
        if (!edges.containsKey(START)) throw new IllegalStateException("graph requires an edge from START");
        edges.forEach((from, destinations) -> { for (String to : destinations) { if (!START.equals(to) && !END.equals(to) && !nodes.containsKey(to)) throw new IllegalStateException("unknown target node: " + to); if (!START.equals(from) && !nodes.containsKey(from)) throw new IllegalStateException("unknown source node: " + from); } });
        conditionalEdges.keySet().forEach(id -> { if (!nodes.containsKey(id)) throw new IllegalStateException("unknown conditional source: " + id); });
        interruptBefore.forEach(id -> { if (!nodes.containsKey(id)) throw new IllegalStateException("unknown interrupt node: " + id); });
        interruptAfter.forEach(id -> { if (!nodes.containsKey(id)) throw new IllegalStateException("unknown interrupt node: " + id); });
        return new CompiledGraph<>(schema, nodes, edges, conditionalEdges, retries, interruptBefore, interruptAfter, options);
    }
    private static void validId(String id) { if (id == null || id.isBlank() || START.equals(id) || END.equals(id)) throw new IllegalArgumentException("invalid node id: " + id); }
    private static void validEndpoint(String id) { if (id == null || id.isBlank()) throw new IllegalArgumentException("endpoint must not be blank"); }
}
