package io.cortavyn.deep;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Portable policy hook for audit, authorization, quota, and data-loss prevention controls. */
public interface WorkspacePolicy {
    default CompletionStage<Void> before(WorkspaceOperation operation) { return CompletableFuture.completedFuture(null); }
    default CompletionStage<Void> after(WorkspaceOperation operation, Throwable failure) { return CompletableFuture.completedFuture(null); }
}
