package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessSandboxTest {
    @Test void runsAnArgumentVectorInConfiguredDirectory() {
        SandboxResult result = new ProcessSandbox(Path.of(".")).execute(List.of("/bin/echo", "hello"), Duration.ofSeconds(5)).toCompletableFuture().join();
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("hello"));
    }
}
