package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class ContainerSandboxTest {
    @Test
    void transfersFilesOnlyInsideItsMountedDirectory() throws Exception {
        ContainerSandbox sandbox = new ContainerSandbox("alpine:3.20", Files.createTempDirectory("cortavyn-container-"), ContainerSandbox.Limits.defaults());
        sandbox.putFile("input/data.txt", "safe".getBytes(StandardCharsets.UTF_8)).toCompletableFuture().join();
        assertArrayEquals("safe".getBytes(StandardCharsets.UTF_8), sandbox.getFile("input/data.txt").toCompletableFuture().join());
        assertThrows(IllegalArgumentException.class, () -> sandbox.putFile("../outside", new byte[0]).toCompletableFuture().join());
    }
}
