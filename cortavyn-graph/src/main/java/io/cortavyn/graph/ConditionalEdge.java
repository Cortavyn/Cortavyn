package io.cortavyn.graph;

import java.util.List;

/** Computes the next nodes from the state visible after a node's update. */
@FunctionalInterface
public interface ConditionalEdge<S> { List<String> route(S state); }
