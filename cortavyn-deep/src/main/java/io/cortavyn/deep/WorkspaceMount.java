package io.cortavyn.deep;

import java.util.Objects;

/** A named subtree delegated by {@link CompositeWorkspace} to one backing workspace. */
public record WorkspaceMount(String prefix, DeepWorkspace workspace) {
    public WorkspaceMount {
        if (prefix == null || prefix.isBlank() || prefix.startsWith("/") || prefix.contains("..")) throw new IllegalArgumentException("prefix must be a safe relative path");
        prefix = prefix.replaceAll("/+$", "");
        Objects.requireNonNull(workspace, "workspace must not be null");
    }
}
