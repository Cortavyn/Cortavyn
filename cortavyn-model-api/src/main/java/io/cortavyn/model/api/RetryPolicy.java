package io.cortavyn.model.api;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

/** Retry rules with exponential backoff and an optional maximum delay cap. */
public record RetryPolicy(int maxAttempts, Duration initialDelay, Duration maxDelay,
                          Predicate<Throwable> retryable) {
    public RetryPolicy {
        if (maxAttempts <= 0) throw new IllegalArgumentException("maxAttempts must be positive");
        initialDelay = requirePositive(initialDelay, "initialDelay");
        maxDelay = requirePositive(maxDelay, "maxDelay");
        if (maxDelay.compareTo(initialDelay) < 0) throw new IllegalArgumentException("maxDelay must not be smaller than initialDelay");
        Objects.requireNonNull(retryable, "retryable must not be null");
    }
    public static RetryPolicy transientFailures(int maxAttempts, Duration initialDelay, Duration maxDelay) {
        return new RetryPolicy(maxAttempts, initialDelay, maxDelay, failure -> true);
    }
    /** Delay before the supplied 1-based retry number. */
    public Duration delayBeforeRetry(int retryNumber) {
        if (retryNumber <= 0) throw new IllegalArgumentException("retryNumber must be positive");
        long multiplier = 1L << Math.min(retryNumber - 1, 30);
        try { return initialDelay.multipliedBy(multiplier).compareTo(maxDelay) > 0 ? maxDelay : initialDelay.multipliedBy(multiplier); }
        catch (ArithmeticException exception) { return maxDelay; }
    }
    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}
