package io.cortavyn.graph;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/** Scheduler limits and runtime services for a compiled graph. */
public record GraphOptions(int maxConcurrency, int recursionLimit, Executor executor, CheckpointStore checkpoints) {
    public GraphOptions {
        if (maxConcurrency < 1) throw new IllegalArgumentException("maxConcurrency must be positive");
        if (recursionLimit < 1) throw new IllegalArgumentException("recursionLimit must be positive");
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(checkpoints, "checkpoints must not be null");
    }
    public static GraphOptions defaults() { return new GraphOptions(16, 100, ForkJoinPool.commonPool(), new InMemoryCheckpointStore()); }
}
