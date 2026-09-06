package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class FileDeepMemoryTest {
    @Test
    void persistsNamespacedMemoryInConfiguredDirectory() throws Exception {
        FileDeepMemory memory = new FileDeepMemory(Files.createTempDirectory("cortavyn-memory-"));
        memory.save("customer-1", "prefer concise answers").toCompletableFuture().join();
        assertEquals("prefer concise answers", memory.load("customer-1").toCompletableFuture().join());
    }
}
