package io.cortavyn.deep;

import java.util.Map;
import java.util.Objects;

/** Immutable portable representation of virtual workspace file contents. */
public record WorkspaceSnapshot(Map<String, String> files) {
    public WorkspaceSnapshot { files = Map.copyOf(Objects.requireNonNull(files, "files must not be null")); }
}
