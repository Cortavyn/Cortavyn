package io.cortavyn.graph;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

/** Per-node retry policy; nodes without one fail the run immediately. */
public record RetryPolicy(int maxAttempts, Duration delay, Predicate<Throwable> retryOn) {
    public RetryPolicy {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        Objects.requireNonNull(delay, "delay must not be null");
        Objects.requireNonNull(retryOn, "retryOn must not be null");
    }
    public static RetryPolicy transientFailures(int maxAttempts, Duration delay) { return new RetryPolicy(maxAttempts, delay, ignored -> true); }
}
