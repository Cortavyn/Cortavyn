package io.cortavyn.graph;

import java.util.Map;
import java.util.Objects;

/** Pauses a run durably until the caller resumes it with an explicit update. */
public record Interrupt(StateUpdate update, Map<String, Object> payload) implements NodeResult {
    public Interrupt { Objects.requireNonNull(update, "update must not be null"); payload = Map.copyOf(Objects.requireNonNull(payload, "payload must not be null")); }
}
