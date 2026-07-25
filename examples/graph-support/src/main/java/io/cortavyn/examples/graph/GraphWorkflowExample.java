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

/**
 * Shared provider-neutral research workflow used by every provider graph example.
 *
 * <p>The example deliberately keeps its data fictional: it demonstrates orchestration and
 * uncertainty handling without pretending that a model response is a verified external fact.</p>
 */
public final class GraphWorkflowExample {
    private GraphWorkflowExample() { }
    public static void run(String provider, ChatModel model, String prompt) {
        // Each graph run keeps intermediate values in an immutable, map-backed state. The
        // explicit channels make every model turn and its hand-off inspectable in checkpoints.
        var schema = StateSchema.builder(GraphState.adapter())
                .channel("question", StateChannel.lastValue())
                .channel("plan", StateChannel.lastValue())
                .channel("research", StateChannel.lastValue())
                .channel("review", StateChannel.lastValue())
                // Topic values are appended instead of overwritten, forming an execution log.
                .channel("events", StateChannel.topic())
                .channel("answer", StateChannel.lastValue())
                .build();

        // The workflow separates planning, evidence-style drafting, quality review, and final
        // synthesis. In production each stage could use a specialist model or a human gate.
        var graph = new StateGraph<>(schema)
                .addNode("plan", (state, runtime) -> complete(model, "Create a compact research plan with three angles for this question. Do not answer it yet:\n" + state.get("question", String.class))
                        .thenApply(text -> new StateUpdate(Map.of("plan", text, "events", "planned by " + provider))))
                .addNode("research", (state, runtime) -> complete(model, "Draft evidence-oriented notes for the plan below. Clearly label assumptions and avoid inventing citations.\nQuestion: " + state.get("question", String.class) + "\nPlan:\n" + state.get("plan", String.class))
                        .thenApply(text -> new StateUpdate(Map.of("research", text, "events", "researched"))))
                .addNode("review", (state, runtime) -> complete(model, "Review these research notes. List unsupported claims, uncertainty, and the most important follow-up question.\n" + state.get("research", String.class))
                        .thenApply(text -> new StateUpdate(Map.of("review", text, "events", "reviewed"))))
                .addNode("synthesize", (state, runtime) -> complete(model, "Write a concise, useful answer to the question using the notes and review below. State uncertainty explicitly; do not claim sources you do not have.\nQuestion: " + state.get("question", String.class) + "\nNotes:\n" + state.get("research", String.class) + "\nReview:\n" + state.get("review", String.class))
                        .thenApply(text -> new StateUpdate(Map.of("answer", text, "events", "synthesized"))))
                .addEdge(StateGraph.START, "plan")
                .addEdge("plan", "research")
                .addEdge("research", "review")
                .addEdge("review", "synthesize")
                .addEdge("synthesize", StateGraph.END)
                .compile();

        // A thread ID groups checkpoints belonging to this provider-specific execution.
        var result = graph.invoke(provider + "-example", new GraphState(Map.of("question", prompt))).toCompletableFuture().join();
        System.out.println("=== Research plan ===\n" + result.state().get("plan", String.class));
        System.out.println("=== Quality review ===\n" + result.state().get("review", String.class));
        System.out.println("=== Final answer ===\n" + result.state().get("answer", String.class));
        // Checkpoint history and Mermaid output make the execution and topology inspectable.
        System.out.println("checkpoints=" + graph.history(provider + "-example").size());
        System.out.println(graph.toMermaid());
    }

    private static java.util.concurrent.CompletionStage<String> complete(ChatModel model, String prompt) {
        return model.complete(new ChatRequest(java.util.List.of(
                        new ChatMessage(ChatMessageRole.SYSTEM, "You are a careful research assistant. Separate facts, assumptions, and uncertainty."),
                        new ChatMessage(ChatMessageRole.USER, prompt))))
                .thenApply(response -> response.message().content());
    }
}
