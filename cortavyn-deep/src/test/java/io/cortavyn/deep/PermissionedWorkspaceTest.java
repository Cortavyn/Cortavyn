package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionedWorkspaceTest {
    @Test void deniesSensitivePathsBeforeBroaderAllowRule() {
        DeepWorkspace workspace = new PermissionedWorkspace(new InMemoryWorkspace(), List.of(WorkspacePermission.deny(".env", WorkspacePermission.Operation.READ, WorkspacePermission.Operation.WRITE), WorkspacePermission.allow("**", WorkspacePermission.Operation.READ, WorkspacePermission.Operation.WRITE)));
        assertThrows(java.util.concurrent.CompletionException.class, () -> workspace.write(".env", "secret").toCompletableFuture().join());
    }
    @Test void filtersDeniedFilesFromDiscoveryOperations() {
        InMemoryWorkspace delegate = new InMemoryWorkspace();
        delegate.write("public.txt", "public").toCompletableFuture().join();
        delegate.write(".env", "secret").toCompletableFuture().join();
        DeepWorkspace workspace = new PermissionedWorkspace(delegate, List.of(WorkspacePermission.deny(".env", WorkspacePermission.Operation.READ), WorkspacePermission.allow("**", WorkspacePermission.Operation.READ)));
        assertEquals(List.of("public.txt"), workspace.glob("**").toCompletableFuture().join());
        assertEquals(List.of("public.txt"), workspace.list(".").toCompletableFuture().join().stream().map(WorkspaceEntry::path).toList());
        assertEquals(List.of("public.txt"), workspace.grep("", "**").toCompletableFuture().join().stream().map(WorkspaceMatch::path).distinct().toList());
    }
}
