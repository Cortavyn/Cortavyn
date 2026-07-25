package io.cortavyn.graph;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Typed state boundary and reducers used by a graph. */
public final class StateSchema<S> {
    private final StateAdapter<S> adapter;
    private final Map<String, StateChannel> channels;
    private StateSchema(Builder<S> builder) { adapter = builder.adapter; channels = Map.copyOf(builder.channels); }
    public static <S> Builder<S> builder(StateAdapter<S> adapter) { return new Builder<>(adapter); }
    public S merge(S state, StateUpdate update) {
        // Reducers are applied per channel; unspecified channels use last-write-wins semantics.
        Map<String, Object> values = new LinkedHashMap<>(adapter.values(state));
        update.values().forEach((key, value) -> {
            @Nullable Object merged = channels.getOrDefault(key, StateChannel.lastValue()).merge(values.get(key), value);
            if (merged == null) values.remove(key); else values.put(key, merged);
        });
        return adapter.create(values);
    }
    public S empty() { return adapter.empty(); }
    public S clearEphemeral(S state) {
        // Ephemeral values may assist a superstep but must never cross its checkpoint boundary.
        Map<String, Object> values = new LinkedHashMap<>(adapter.values(state));
        channels.forEach((key, channel) -> { if (channel.ephemeral()) values.remove(key); });
        return adapter.create(values);
    }
    public Map<String, Object> values(S state) { return Map.copyOf(adapter.values(state)); }
    public static final class Builder<T> {
        private final StateAdapter<T> adapter;
        private final Map<String, StateChannel> channels = new LinkedHashMap<>();
        private Builder(StateAdapter<T> adapter) { this.adapter = Objects.requireNonNull(adapter, "adapter must not be null"); }
        public Builder<T> channel(String name, StateChannel channel) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("channel name must not be blank");
            if (channels.putIfAbsent(name, Objects.requireNonNull(channel, "channel must not be null")) != null) throw new IllegalArgumentException("duplicate channel: " + name);
            return this;
        }
        public StateSchema<T> build() { return new StateSchema<>(this); }
    }
}
