package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemWorkspaceTest {
    @TempDir Path root;

    @Test
    void keepsHostAccessInsideTheRootAndSupportsRecursiveGlobs() {
        FilesystemWorkspace workspace = new FilesystemWorkspace(root);
        workspace.write("notes/one.txt", "first").toCompletableFuture().join();
        workspace.write("notes/two.md", "second").toCompletableFuture().join();

        assertEquals(List.of("notes/one.txt"), workspace.glob("**/*.txt").toCompletableFuture().join());
        assertEquals("first", workspace.read("notes/one.txt").toCompletableFuture().join());
        assertThrows(IllegalArgumentException.class, () -> workspace.resolve("../outside.txt"));
    }
}
