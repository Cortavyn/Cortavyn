package io.cortavyn.graph;

import java.util.Map;

/** Converts an application-owned state type to the graph's named channels. */
public interface StateAdapter<S> {
    S empty();
    Map<String, Object> values(S state);
    S create(Map<String, Object> values);
}
