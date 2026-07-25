package io.cortavyn.graph;

import java.util.Set;

/** Selects categories emitted from a graph stream. */
public enum StreamMode {
    VALUES, UPDATES, DEBUG, CUSTOM, CHECKPOINTS;
    boolean accepts(GraphEventType type) {
        return switch (this) {
            case VALUES -> type == GraphEventType.VALUE;
            case UPDATES -> type == GraphEventType.UPDATE;
            case DEBUG -> Set.of(GraphEventType.NODE_STARTED, GraphEventType.NODE_COMPLETED, GraphEventType.RETRY, GraphEventType.FAILED, GraphEventType.CACHE_HIT, GraphEventType.CACHE_MISS).contains(type);
            case CUSTOM -> type == GraphEventType.CUSTOM;
            case CHECKPOINTS -> type == GraphEventType.CHECKPOINT || type == GraphEventType.INTERRUPTED;
        };
    }
}
