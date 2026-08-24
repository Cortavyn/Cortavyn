package io.cortavyn.deep;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Applies a policy consistently around every workspace operation. */
public final class PolicyWorkspace implements DeepWorkspace, DeepWorkspaceFiles {
    private final DeepWorkspace delegate;
    private final WorkspacePolicy policy;
    public PolicyWorkspace(DeepWorkspace delegate, WorkspacePolicy policy) { this.delegate = Objects.requireNonNull(delegate, "delegate must not be null"); this.policy = Objects.requireNonNull(policy, "policy must not be null"); }
    @Override public CompletionStage<List<WorkspaceEntry>> list(String path) { return call(WorkspaceOperation.Kind.LIST, path, () -> delegate.list(path)); }
    @Override public CompletionStage<String> read(String path) { return call(WorkspaceOperation.Kind.READ, path, () -> delegate.read(path)); }
    @Override public CompletionStage<Void> write(String path, String content) { return call(WorkspaceOperation.Kind.WRITE, path, () -> delegate.write(path, content)); }
    @Override public CompletionStage<Boolean> edit(String path, String expected, String replacement, boolean all) { return call(WorkspaceOperation.Kind.EDIT, path, () -> delegate.edit(path, expected, replacement, all)); }
    @Override public CompletionStage<List<String>> glob(String pattern) { return call(WorkspaceOperation.Kind.GLOB, pattern, () -> delegate.glob(pattern)); }
    @Override public CompletionStage<List<WorkspaceMatch>> grep(String query, String pattern) { return call(WorkspaceOperation.Kind.GREP, pattern, () -> delegate.grep(query, pattern)); }
    @Override public CompletionStage<WorkspaceRead> readFile(String path, int offset, int limit) {
        if (!(delegate instanceof DeepWorkspaceFiles files)) return java.util.concurrent.CompletableFuture.failedStage(new UnsupportedOperationException("bounded reads are not supported by the wrapped workspace"));
        return call(WorkspaceOperation.Kind.READ, path, () -> files.readFile(path, offset, limit));
    }
    private <T> CompletionStage<T> call(WorkspaceOperation.Kind kind, String path, java.util.function.Supplier<CompletionStage<T>> action) {
        WorkspaceOperation operation = new WorkspaceOperation(kind, path);
        return policy.before(operation).thenCompose(ignored -> action.get().whenComplete((value, failure) -> policy.after(operation, failure)));
    }
}
