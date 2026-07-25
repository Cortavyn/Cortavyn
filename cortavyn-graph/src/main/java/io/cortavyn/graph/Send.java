package io.cortavyn.graph;

import java.util.Objects;

/** Dynamically schedules a target node, enabling map-reduce fan-out. */
public record Send(String target, StateUpdate update) implements NodeResult {
    public Send { if (target == null || target.isBlank()) throw new IllegalArgumentException("target must not be blank"); Objects.requireNonNull(update, "update must not be null"); }
}
