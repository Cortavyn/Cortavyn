package io.cortavyn.deep;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Serializable result of a {@link DeepInterpreter} evaluation. */
public record DeepInterpreterResult(
        String value,
        List<ConsoleMessage> console,
        @Nullable String error) {
    public DeepInterpreterResult {
        Objects.requireNonNull(value, "value must not be null");
        console = List.copyOf(Objects.requireNonNull(console, "console must not be null"));
    }

    public static DeepInterpreterResult success(String value, List<ConsoleMessage> console) {
        return new DeepInterpreterResult(value, console, null);
    }

    public static DeepInterpreterResult failure(String message, List<ConsoleMessage> console) {
        return new DeepInterpreterResult("", console, Objects.requireNonNull(message, "message must not be null"));
    }

    /** A console entry emitted by evaluated code. */
    public record ConsoleMessage(Level level, String text) {
        public ConsoleMessage {
            Objects.requireNonNull(level, "level must not be null");
            Objects.requireNonNull(text, "text must not be null");
        }
    }

    public enum Level { LOG, WARN, ERROR }
}
