package io.cortavyn.examples.deep;

import io.cortavyn.deep.InMemoryWorkspace;

/** Reads a deterministic line window without loading a complete workspace file into an agent context. */
public final class BoundedWorkspaceReadExample {
    private BoundedWorkspaceReadExample() { }

    public static void main(String[] arguments) {
        InMemoryWorkspace workspace = new InMemoryWorkspace();
        workspace.write("notes.txt", "first\nsecond\nthird\nfourth").toCompletableFuture().join();
        System.out.println(workspace.readFile("notes.txt", 1, 2).toCompletableFuture().join());
    }
}
