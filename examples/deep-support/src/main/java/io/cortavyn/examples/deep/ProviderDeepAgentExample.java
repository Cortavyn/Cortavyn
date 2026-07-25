package io.cortavyn.examples.deep;

import io.cortavyn.deep.ApprovalDecision;
import io.cortavyn.deep.DeepAgent;
import io.cortavyn.deep.DeepRun;
import io.cortavyn.deep.DeepSubagent;
import io.cortavyn.deep.InMemoryWorkspace;
import io.cortavyn.model.api.ChatModel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

/**
 * Provider-neutral, human-reviewed Deep Agent walkthrough.
 *
 * <p>The model plans its work with todos, delegates a short critique to an isolated specialist,
 * writes a reviewed briefing into a virtual workspace, and is resumed after that write has been
 * approved. It deliberately uses only the built-in deep-agent tools, so every provider entry
 * exercises the same harness behaviour.</p>
 */
public final class ProviderDeepAgentExample {
    private ProviderDeepAgentExample() {
    }

    public static void run(String provider, ChatModel model, String question) {
        String threadId = "provider-deep-demo";
        InMemoryWorkspace workspace = new InMemoryWorkspace();
        DeepAgent agent = DeepAgent.builder(model)
                .systemPrompt("""
                        You are a careful research lead. You MUST use tools rather than merely
                        describing their outcome. First use write_todos to plan the work.
                        Delegate a concise independent risk critique to the reviewer specialist.
                        Then use write_file to create brief.md with a practical answer, including
                        risks and mitigations. Read the file back to verify it. Do not return a
                        final narrative until write_file and read_file have returned successfully.
                        Do not claim that tools were used unless their results are in the conversation.
                        """)
                .workspace(workspace)
                .subagents(new DeepSubagent(
                        "reviewer",
                        "Independently identifies omissions and operational risks.",
                        "You are a rigorous reviewer. Return a concise risk checklist and mitigations.",
                        List.of()))
                .build();

        DeepRun run = agent.invoke(threadId, question).toCompletableFuture().join();
        if (run.interrupt() != null) {
            run = agent.resume(threadId, requestApproval(provider, run)).toCompletableFuture().join();
        }

        System.out.printf("%s deep-agent status: %s%n", provider, run.graphStatus());
        System.out.println("Todos: " + run.todos());
        if (workspace.list("").toCompletableFuture().join().stream().anyMatch(entry -> entry.path().equals("brief.md"))) {
            System.out.println("Workspace briefing:\n" + workspace.read("brief.md").toCompletableFuture().join());
        } else {
            System.out.println("Workspace briefing: not created (the provider did not issue write_file).");
        }
        System.out.println("Checkpoints: " + agent.history(threadId).size());
        System.out.println("Final response:\n" + run.conversation().messages().getLast().content());
    }

    /** Turns a durable approval interrupt into an explicit terminal decision. */
    private static List<ApprovalDecision> requestApproval(String provider, DeepRun run) {
        Scanner input = new Scanner(System.in, StandardCharsets.UTF_8);
        return Objects.requireNonNull(run.interrupt(), "an approval interrupt is required").actions().stream().map(action -> {
            System.out.printf("%s requests approval: %s %s%nApprove? [y/N] ", provider, action.toolName(), action.arguments());
            String answer = input.hasNextLine() ? input.nextLine().trim() : "";
            return answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")
                    ? new ApprovalDecision(ApprovalDecision.Type.APPROVE, null, null)
                    : new ApprovalDecision(ApprovalDecision.Type.REJECT, null, "Rejected in the example terminal.");
        }).toList();
    }
}
