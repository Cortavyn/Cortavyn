package io.cortavyn.graph;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Final observable result of one graph invocation. */
public record RunResult<S>(String threadId, String runId, GraphStatus status, S state, String checkpointId,
                           @Nullable ResumeToken resumeToken) {
    public RunResult { Objects.requireNonNull(threadId, "threadId must not be null"); Objects.requireNonNull(runId, "runId must not be null"); Objects.requireNonNull(status, "status must not be null"); Objects.requireNonNull(state, "state must not be null"); Objects.requireNonNull(checkpointId, "checkpointId must not be null"); }
}
