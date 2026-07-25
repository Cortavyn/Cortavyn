package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryWorkspaceTest {
    @Test
    void supportsSafeFileOperations() {
        var workspace = new InMemoryWorkspace();
        workspace.write("notes/a.txt", "one\ntwo\none").toCompletableFuture().join();
        assertEquals(List.of("notes/a.txt"), workspace.glob("**/*.txt").toCompletableFuture().join());
        assertEquals(2, workspace.grep("one", "**/*.txt").toCompletableFuture().join().size());
        assertEquals(true, workspace.edit("notes/a.txt", "one", "three", false).toCompletableFuture().join());
        assertEquals("three\ntwo\none", workspace.read("notes/a.txt").toCompletableFuture().join());
        assertFalse(workspace.edit("notes/a.txt", "missing", "x", false).toCompletableFuture().join());
    }

    @Test
    void rejectsPathTraversal() {
        var workspace = new InMemoryWorkspace();
        assertThrows(IllegalArgumentException.class, () -> workspace.write("../secret", "x"));
        assertThrows(IllegalArgumentException.class, () -> workspace.read("/secret"));
    }
}
