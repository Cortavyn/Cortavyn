package io.cortavyn.graph;

import java.util.List;
import java.util.Objects;

/** A dynamic fan-out of independently configured {@link Send} operations. */
public record Sends(List<Send> sends) implements NodeResult {
    public Sends { sends = List.copyOf(Objects.requireNonNull(sends, "sends must not be null")); }
    @Override public StateUpdate update() { return StateUpdate.empty(); }
}
