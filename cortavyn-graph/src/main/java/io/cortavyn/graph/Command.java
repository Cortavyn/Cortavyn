package io.cortavyn.graph;

import java.util.List;
import java.util.Objects;

/** Combines a state update with explicit control flow. */
public record Command(StateUpdate update, List<String> gotoNodes) implements NodeResult {
    public Command { Objects.requireNonNull(update, "update must not be null"); gotoNodes = List.copyOf(Objects.requireNonNull(gotoNodes, "gotoNodes must not be null")); }
    public Command(StateUpdate update, String gotoNode) { this(update, List.of(gotoNode)); }
}
