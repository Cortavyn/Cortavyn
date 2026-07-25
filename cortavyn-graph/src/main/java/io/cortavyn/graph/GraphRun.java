package io.cortavyn.graph;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Publisher;

/** Completion handle and reactive stream for one invocation. */
public record GraphRun<S>(CompletionStage<RunResult<S>> completion, Publisher<GraphEvent> events) {
    public GraphRun { Objects.requireNonNull(completion, "completion must not be null"); Objects.requireNonNull(events, "events must not be null"); }
}
