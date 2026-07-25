package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class SubagentRegistryTest {
    @Test
    void restartsAPersistedRunningTaskWhenTheOriginalProcessIsGone() {
        InMemoryDeepTaskStore store = new InMemoryDeepTaskStore();
        store.save(new DeepSubagentTask("task-1", "researcher", "find facts", DeepSubagentTask.Status.RUNNING, null, null)).toCompletableFuture().join();
        SubagentRegistry registry = new SubagentRegistry(store, (agent, prompt) -> CompletableFuture.completedFuture(agent + ": " + prompt));

        assertEquals("researcher: find facts", registry.await("task-1").toCompletableFuture().join());
        assertEquals(DeepSubagentTask.Status.COMPLETED, store.get("task-1").toCompletableFuture().join().orElseThrow().status());
    }
}
