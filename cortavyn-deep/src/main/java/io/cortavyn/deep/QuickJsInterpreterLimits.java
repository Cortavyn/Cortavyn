package io.cortavyn.deep;

import java.time.Duration;
import java.util.Objects;

/** Resource limits for one thread-scoped QuickJS runtime. */
public record QuickJsInterpreterLimits(
        Duration executionTimeout,
        long maxMemoryBytes,
        long maxStackBytes,
        int maxOutputCharacters) {
    public QuickJsInterpreterLimits {
        Objects.requireNonNull(executionTimeout, "executionTimeout must not be null");
        if (executionTimeout.isZero() || executionTimeout.isNegative()
                || maxMemoryBytes <= 0 || maxStackBytes <= 0 || maxOutputCharacters <= 0) {
            throw new IllegalArgumentException("interpreter limits must be positive");
        }
    }

    public static QuickJsInterpreterLimits defaults() {
        return new QuickJsInterpreterLimits(Duration.ofSeconds(5), 64L * 1024 * 1024, 320L * 1024, 16_000);
    }
}
