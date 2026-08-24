package io.cortavyn.deep;

import io.cortavyn.model.api.ChatContent;
import java.util.List;
import java.util.Objects;

/** Bounded workspace-read result with portable content blocks and source metadata. */
public record WorkspaceRead(List<ChatContent> content, int offset, int lineCount, boolean truncated, long size) {
    public WorkspaceRead {
        content = List.copyOf(Objects.requireNonNull(content, "content must not be null"));
        if (offset < 0 || lineCount < 0 || size < 0) throw new IllegalArgumentException("workspace read metadata must not be negative");
    }
}
