package io.cortavyn.chat;

/** Receives non-model progress emitted by a tool during an agent run. */
@FunctionalInterface
public interface ToolProgressWriter {
    void write(ToolProgress progress);

    static ToolProgressWriter noop() {
        return ignored -> { };
    }
}
