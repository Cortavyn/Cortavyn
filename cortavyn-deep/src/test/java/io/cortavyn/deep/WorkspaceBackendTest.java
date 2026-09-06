package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.cortavyn.model.api.TextContent;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class WorkspaceBackendTest {
    @Test
    void routesMountsAndPersistsThroughStoreBackend() {
        StoreWorkspace store = new StoreWorkspace(new InMemoryWorkspaceStore());
        InMemoryWorkspace scratch = new InMemoryWorkspace();
        CompositeWorkspace workspace = new CompositeWorkspace(List.of(new WorkspaceMount("memory", store), new WorkspaceMount("scratch", scratch)));

        workspace.write("memory/facts.txt", "one\ntwo").toCompletableFuture().join();
        workspace.write("scratch/temp.txt", "three").toCompletableFuture().join();

        assertEquals("one\ntwo", workspace.read("memory/facts.txt").toCompletableFuture().join());
        assertEquals(List.of("memory/facts.txt", "scratch/temp.txt"), workspace.glob("**/*.txt").toCompletableFuture().join());
        assertEquals("1: one", ((TextContent) store.readFile("facts.txt", 0, 1).toCompletableFuture().join().content().getFirst()).text());
    }

    @Test
    void policyCanRejectBeforeAWriteReachesTheBackend() {
        PolicyWorkspace workspace = new PolicyWorkspace(new InMemoryWorkspace(), new WorkspacePolicy() {
            @Override public java.util.concurrent.CompletionStage<Void> before(WorkspaceOperation operation) {
                return operation.kind() == WorkspaceOperation.Kind.WRITE ? java.util.concurrent.CompletableFuture.failedStage(new SecurityException("writes disabled")) : WorkspacePolicy.super.before(operation);
            }
        });

        assertThrows(CompletionException.class, () -> workspace.write("blocked.txt", "no").toCompletableFuture().join());
    }
}
