package io.cortavyn.graph;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Durable state snapshot after a graph superstep. */
public record Checkpoint(String id, String threadId, String runId, @Nullable String parentId, GraphStatus status,
                         Instant createdAt, Map<String, Object> state, List<CheckpointTask> nextTasks,
                         @Nullable Map<String, Object> interruptPayload, @Nullable String failure) {
    public Checkpoint {
        Objects.requireNonNull(id, "id must not be null"); Objects.requireNonNull(threadId, "threadId must not be null");
        Objects.requireNonNull(runId, "runId must not be null"); Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null"); state = Map.copyOf(Objects.requireNonNull(state, "state must not be null"));
        nextTasks = List.copyOf(Objects.requireNonNull(nextTasks, "nextTasks must not be null"));
        if (interruptPayload != null) interruptPayload = Map.copyOf(interruptPayload);
    }
}
