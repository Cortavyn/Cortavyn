package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryDeepStateTest {
    @Test void isolatesMemoryByNamespace() {
        var memory = new InMemoryDeepMemory();
        memory.save("alice", "prefer concise answers").toCompletableFuture().join();
        assertEquals("prefer concise answers", memory.load("alice").toCompletableFuture().join());
        assertEquals("", memory.load("bob").toCompletableFuture().join());
    }
    @Test void storesTodosAndAsyncTaskResults() {
        var todos = new InMemoryDeepTodoStore();
        todos.replace("thread", List.of(new DeepTodo("1", "research", DeepTodo.Status.IN_PROGRESS))).toCompletableFuture().join();
        assertEquals(1, todos.read("thread").toCompletableFuture().join().size());
        var tasks = new InMemoryDeepTaskStore();
        tasks.save(new DeepSubagentTask("task", "researcher", "find facts", DeepSubagentTask.Status.COMPLETED, "report", null)).toCompletableFuture().join();
        assertTrue(tasks.get("task").toCompletableFuture().join().isPresent());
    }
}
