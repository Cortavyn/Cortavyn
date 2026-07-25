package io.cortavyn.examples.graph;

import io.cortavyn.graph.GraphState;
import io.cortavyn.graph.StateChannel;
import io.cortavyn.graph.StateGraph;
import io.cortavyn.graph.StateSchema;
import io.cortavyn.graph.StateUpdate;
import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatRequest;
import java.util.Map;

/** Shared provider-neutral workflow used by every provider graph example. */
public final class GraphWorkflowExample {
    private GraphWorkflowExample() { }
    public static void run(String provider, ChatModel model, String prompt) {
        // Each graph run keeps all intermediate values in an immutable, map-backed state.
        var schema = StateSchema.builder(GraphState.adapter())
                // Topic values are appended instead of overwritten, forming a small execution log.
                .channel("events", StateChannel.topic())
                // Draft and answer are normal last-write-wins values.
                .channel("draft", StateChannel.lastValue())
                .channel("answer", StateChannel.lastValue())
                .build();

        // The first node delegates drafting to the provider-specific model.
        var graph = new StateGraph<>(schema)
                .addNode("draft", (state, runtime) -> model.complete(new ChatRequest(java.util.List.of(new ChatMessage(ChatMessageRole.USER, "Draft a concise answer: " + prompt))))
                        .thenApply(response -> new StateUpdate(Map.of("draft", response.message().content(), "events", "drafted by " + provider))))
                // The second node sees the draft in state and turns it into the final answer.
                .addNode("finalize", (state, runtime) -> model.complete(new ChatRequest(java.util.List.of(new ChatMessage(ChatMessageRole.USER, "Improve this draft and return only the final answer: " + state.get("draft", String.class)))))
                        .thenApply(response -> new StateUpdate(Map.of("answer", response.message().content(), "events", "finalized"))))
                .addEdge(StateGraph.START, "draft")
                // Routing is explicit: a draft always continues into the finalization node.
                .addConditionalEdges("draft", ignored -> java.util.List.of("finalize"))
                .addEdge("finalize", StateGraph.END)
                .compile();

        // A thread ID groups checkpoints belonging to this workflow execution.
        var result = graph.invoke(provider + "-example", GraphState.empty()).toCompletableFuture().join();
        System.out.println(result.state().get("answer", String.class));
        // Checkpoint history and Mermaid output make the execution and topology inspectable.
        System.out.println("checkpoints=" + graph.history(provider + "-example").size());
        System.out.println(graph.toMermaid());
    }
}
