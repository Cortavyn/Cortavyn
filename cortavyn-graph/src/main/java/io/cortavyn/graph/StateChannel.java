package io.cortavyn.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BinaryOperator;
import org.jspecify.annotations.Nullable;

/** Defines how concurrent updates to one named state value are combined. */
public interface StateChannel {
    @Nullable Object merge(@Nullable Object current, @Nullable Object update);
    default boolean ephemeral() { return false; }

    static StateChannel lastValue() { return (current, update) -> update; }
    static StateChannel reducer(BinaryOperator<Object> reducer) {
        Objects.requireNonNull(reducer, "reducer must not be null");
        return (current, update) -> current == null ? update : reducer.apply(current, update);
    }
    static StateChannel topic() { return reducer((current, update) -> {
        List<Object> values = new ArrayList<>();
        if (current instanceof List<?> list) values.addAll(list); else if (current != null) values.add(current);
        if (update instanceof List<?> list) values.addAll(list); else values.add(update);
        return List.copyOf(values);
    }); }
    static StateChannel ephemeralValue() { return new StateChannel() {
        @Override public @Nullable Object merge(@Nullable Object current, @Nullable Object update) { return update; }
        @Override public boolean ephemeral() { return true; }
    }; }
}
