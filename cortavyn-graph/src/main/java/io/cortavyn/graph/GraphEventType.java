package io.cortavyn.graph;

/** Categories available through graph streaming. */
public enum GraphEventType { VALUE, UPDATE, NODE_STARTED, NODE_COMPLETED, RETRY, CHECKPOINT, INTERRUPTED, CUSTOM, FAILED, CACHE_HIT, CACHE_MISS }
