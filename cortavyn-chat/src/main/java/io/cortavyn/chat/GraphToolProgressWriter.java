package io.cortavyn.chat;

import io.cortavyn.graph.NodeRuntime;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Routes application tool progress into the graph's custom event stream. */
public final class GraphToolProgressWriter implements ToolProgressWriter {
    private final NodeRuntime runtime;
    public GraphToolProgressWriter(NodeRuntime runtime) { this.runtime = Objects.requireNonNull(runtime, "runtime must not be null"); }
    @Override public void write(ToolProgress progress) {
        // Preserve application attributes while adding the human-readable progress message.
        var event = new LinkedHashMap<>(progress.attributes());
        event.put("message", progress.message());
        runtime.emit(event);
    }
}
