package io.cortavyn.deep;

/** One normalized filesystem action passing through a {@link WorkspacePolicy}. */
public record WorkspaceOperation(Kind kind, String path) {
    public WorkspaceOperation {
        if (kind == null) throw new IllegalArgumentException("kind must not be null");
        if (path == null || path.isBlank()) throw new IllegalArgumentException("path must not be blank");
    }
    public enum Kind { LIST, READ, WRITE, EDIT, GLOB, GREP }
}
