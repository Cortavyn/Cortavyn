package io.cortavyn.deep;

import java.util.List;

/** Ordered path rule for workspace read/write operations. */
public record WorkspacePermission(List<Operation> operations, String pattern, Mode mode) {
    public WorkspacePermission { operations = List.copyOf(operations); if (operations.isEmpty()) throw new IllegalArgumentException("operations must not be empty"); if (pattern == null || pattern.isBlank()) throw new IllegalArgumentException("pattern must not be blank"); }
    public enum Operation { READ, WRITE }
    public enum Mode { ALLOW, DENY }
    public static WorkspacePermission allow(String pattern, Operation... operations) { return new WorkspacePermission(List.of(operations), pattern, Mode.ALLOW); }
    public static WorkspacePermission deny(String pattern, Operation... operations) { return new WorkspacePermission(List.of(operations), pattern, Mode.DENY); }
}
