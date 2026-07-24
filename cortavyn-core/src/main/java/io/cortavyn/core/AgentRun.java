package io.cortavyn.core;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Immutable snapshot of an agent run. */
public record AgentRun(
        AgentRunId id,
        AgentRunState state,
        Instant createdAt,
        Map<String, Object> attributes) {
    public AgentRun {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes must not be null"));
    }
}
