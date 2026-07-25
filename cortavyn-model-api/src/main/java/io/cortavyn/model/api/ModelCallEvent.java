package io.cortavyn.model.api;

import org.jspecify.annotations.Nullable;

/** Completed call data; exactly one of response and failure is present. */
public record ModelCallEvent(ChatRequest request, @Nullable ChatResponse response, @Nullable Throwable failure,
                             long durationNanos) {
    public ModelCallEvent {
        if (durationNanos < 0) throw new IllegalArgumentException("durationNanos must not be negative");
        if ((response == null) == (failure == null)) throw new IllegalArgumentException("exactly one of response or failure must be present");
    }
}
