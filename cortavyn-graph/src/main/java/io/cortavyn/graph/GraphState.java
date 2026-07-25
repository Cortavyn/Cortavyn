package io.cortavyn.graph;

import java.util.Map;
import java.util.Objects;

/** Immutable map-backed state suitable for dynamic graphs. Applications may supply a typed adapter. */
public record GraphState(Map<String, Object> values) {
    public GraphState { values = Map.copyOf(Objects.requireNonNull(values, "values must not be null")); }
    public static GraphState empty() { return new GraphState(Map.of()); }
    public <T> T get(String key, Class<T> type) { return type.cast(Objects.requireNonNull(values.get(key), "missing state value: " + key)); }
    public static StateAdapter<GraphState> adapter() { return new StateAdapter<>() {
        @Override public GraphState empty() { return GraphState.empty(); }
        @Override public Map<String, Object> values(GraphState state) { return state.values(); }
        @Override public GraphState create(Map<String, Object> values) { return new GraphState(values); }
    }; }
}
