package io.cortavyn.examples.deep;

import io.cortavyn.deep.ApprovalDecision;
import io.cortavyn.deep.DeepAgent;
import io.cortavyn.deep.DeepRun;
import io.cortavyn.deep.DeepSubagent;
import io.cortavyn.deep.InMemoryWorkspace;
import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatResponse;
import io.cortavyn.model.api.ToolCall;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Runnable, provider-free walkthrough of a reviewed deep-agent workspace write. */
public final class DeepAgentExample {
    private DeepAgentExample() { }
    public static void main(String[] arguments) {
        InMemoryWorkspace workspace = new InMemoryWorkspace();
        DeepAgent agent = DeepAgent.builder(new ScriptedModel())
                .systemPrompt("Write concise research notes.")
                .workspace(workspace)
                .subagents(new DeepSubagent("researcher", "Finds evidence in an isolated context.", "You are a research specialist.", List.of()))
                .build();

        DeepRun paused = agent.invoke("demo", "Save a finding in notes/finding.txt").toCompletableFuture().join();
        System.out.println("approval required for " + java.util.Objects.requireNonNull(paused.interrupt(), "the scripted run must request approval").actions().getFirst().toolName());
        DeepRun finished = agent.resume("demo", List.of(new ApprovalDecision(ApprovalDecision.Type.APPROVE, null, null))).toCompletableFuture().join();
        System.out.println(finished.conversation().messages().getLast().content());
        System.out.println(workspace.read("notes/finding.txt").toCompletableFuture().join());
    }

    private static final class ScriptedModel implements ChatModel {
        private int calls;
        @Override public java.util.concurrent.CompletionStage<ChatResponse> complete(io.cortavyn.model.api.ChatRequest request) {
            calls++;
            ChatMessage message = calls == 1
                    ? new ChatMessage(ChatMessageRole.ASSISTANT, "", List.of(), null, List.of(new ToolCall("write-finding", "write_file", Map.of("path", "notes/finding.txt", "content", "Durable state keeps the research trail."))), Map.of())
                    : new ChatMessage(ChatMessageRole.ASSISTANT, "Finding saved.");
            return CompletableFuture.completedFuture(new ChatResponse(message));
        }
    }
}
