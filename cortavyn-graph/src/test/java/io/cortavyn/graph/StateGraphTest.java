package io.cortavyn.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StateGraphTest {
    private static StateSchema<GraphState> schema() {
        return StateSchema.builder(GraphState.adapter())
                .channel("steps", StateChannel.topic())
                .channel("answer", StateChannel.lastValue())
                .build();
    }

    @Test
    void runsASequenceAndCombinesUpdates() {
        var graph = new StateGraph<>(schema())
                .addNode("one", (state, runtime) -> CompletableFuture.completedFuture(new StateUpdate(Map.of("steps", "one"))))
                .addNode("two", (state, runtime) -> CompletableFuture.completedFuture(new StateUpdate(Map.of("steps", "two", "answer", 42))))
                .addEdge(StateGraph.START, "one").addEdge("one", "two").addEdge("two", StateGraph.END).compile();

        var result = graph.invoke("thread-1", GraphState.empty()).toCompletableFuture().join();

        assertEquals(GraphStatus.SUCCEEDED, result.status());
        assertEquals(List.of("one", "two"), result.state().get("steps", List.class));
        assertEquals(42, result.state().get("answer", Integer.class));
        assertEquals(3, graph.history("thread-1").size());
    }

    @Test
    void persistsInterruptAndResumesWithCallerUpdate() {
        var graph = new StateGraph<>(schema())
                .addNode("approval", (state, runtime) -> CompletableFuture.completedFuture(new StateUpdate(Map.of("steps", "approved"))))
                .addEdge(StateGraph.START, "approval").addEdge("approval", StateGraph.END).interruptBefore("approval").compile();
        var paused = graph.invoke("thread-2", GraphState.empty()).toCompletableFuture().join();

        assertEquals(GraphStatus.INTERRUPTED, paused.status());
        assertNotNull(paused.resumeToken());
        var resumed = graph.resume(paused.resumeToken(), new StateUpdate(Map.of("answer", 7))).toCompletableFuture().join();

        assertEquals(GraphStatus.SUCCEEDED, resumed.status());
        assertEquals(7, resumed.state().get("answer", Integer.class));
    }

    @Test
    void commandRoutesToSelectedNode() {
        var graph = new StateGraph<>(schema())
                .addNode("route", (state, runtime) -> CompletableFuture.completedFuture(new Command(new StateUpdate(Map.of()), "yes")))
                .addNode("yes", (state, runtime) -> CompletableFuture.completedFuture(new StateUpdate(Map.of("answer", "yes"))))
                .addEdge(StateGraph.START, "route").addEdge("yes", StateGraph.END).compile();

        assertEquals("yes", graph.invoke("thread-3", GraphState.empty()).toCompletableFuture().join().state().get("answer", String.class));
    }

    @Test
    void sendFanOutGivesEachWorkerItsOwnInputState() {
        var graph = new StateGraph<>(schema())
                .addNode("fanout", (state, runtime) -> CompletableFuture.completedFuture(new Sends(List.of(
                        new Send("worker", new StateUpdate(Map.of("item", "a"))),
                        new Send("worker", new StateUpdate(Map.of("item", "b")))))))
                .addNode("worker", (state, runtime) -> CompletableFuture.completedFuture(new StateUpdate(Map.of("steps", state.get("item", String.class)))))
                .addEdge(StateGraph.START, "fanout").addEdge("worker", StateGraph.END).compile();

        var result = graph.invoke("thread-4", GraphState.empty()).toCompletableFuture().join();

        assertEquals(List.of("a", "b"), result.state().get("steps", List.class));
    }

    @Test
    void retriesConfiguredNodesAndCachesTasks() {
        var attempts = new AtomicInteger();
        var taskCalls = new AtomicInteger();
        var cache = new InMemoryTaskCache();
        var graph = new StateGraph<>(schema())
                .addNode("retry", (state, runtime) -> attempts.incrementAndGet() == 1
                        ? CompletableFuture.failedFuture(new IllegalStateException("temporary"))
                        : CompletableFuture.completedFuture(StateUpdate.empty()), RetryPolicy.transientFailures(2, java.time.Duration.ZERO))
                .addNode("cached", new CachedTaskNode<>(ignored -> CompletableFuture.completedFuture(taskCalls.incrementAndGet()), ignored -> "input", ignored -> "key", (state, value) -> new StateUpdate(Map.of("answer", value)), cache))
                .addEdge(StateGraph.START, "retry").addEdge("retry", "cached").addEdge("cached", StateGraph.END).compile();

        graph.invoke("thread-5", GraphState.empty()).toCompletableFuture().join();
        var second = graph.invoke("thread-6", GraphState.empty()).toCompletableFuture().join();

        assertEquals(3, attempts.get());
        assertEquals(1, taskCalls.get());
        assertEquals(1, second.state().get("answer", Integer.class));
    }
}
