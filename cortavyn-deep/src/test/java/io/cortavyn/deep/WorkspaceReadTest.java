package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cortavyn.model.api.TextContent;
import io.cortavyn.model.api.ToolCall;
import io.cortavyn.model.api.ImageContent;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkspaceReadTest {
    @Test
    void readsALineWindowAndReportsRemainingContent() {
        InMemoryWorkspace workspace = new InMemoryWorkspace();
        workspace.write("notes.txt", "one\ntwo\nthree\nfour").toCompletableFuture().join();

        WorkspaceRead read = workspace.readFile("notes.txt", 1, 2).toCompletableFuture().join();

        assertEquals(List.of(new TextContent("2: two\n3: three")), read.content());
        assertEquals(1, read.offset());
        assertEquals(4, read.lineCount());
        assertTrue(read.truncated());
    }

    @Test
    void readFileToolKeepsItsSimpleFormAndExposesOptionalWindowArguments() {
        InMemoryWorkspace workspace = new InMemoryWorkspace();
        workspace.write("notes.txt", "one\ntwo\nthree").toCompletableFuture().join();
        var tool = DeepTools.workspace(workspace).stream().filter(candidate -> candidate.definition().name().equals("read_file")).findFirst().orElseThrow();

        var full = tool.executor().execute(new ToolCall("full", "read_file", java.util.Map.of("path", "notes.txt"))).toCompletableFuture().join();
        var window = tool.executor().execute(new ToolCall("window", "read_file", java.util.Map.of("path", "notes.txt", "offset", 1, "limit", 1))).toCompletableFuture().join();

        assertEquals("one\ntwo\nthree", full.content());
        assertEquals("2: two", window.content());
        assertEquals(java.util.List.of("path"), tool.definition().inputSchema().get("required"));
        assertEquals(true, window.metadata().get("truncated"));
    }

    @Test
    void filesystemWorkspaceReturnsMediaAsPortableContent() throws Exception {
        java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("cortavyn-workspace-");
        java.nio.file.Files.write(directory.resolve("diagram.png"), new byte[] {0});
        WorkspaceRead read = new FilesystemWorkspace(directory).readFile("diagram.png", 0, 10).toCompletableFuture().join();

        ImageContent image = (ImageContent) read.content().getFirst();
        assertEquals("image/png", image.mediaType());
        assertEquals("data", image.uri().getScheme());
    }
}
