package io.cortavyn.core;

import java.util.Objects;

/** A stable identifier for a durable agent run. */
public record AgentRunId(String value) {
    public AgentRunId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
