package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cortavyn.chat.ChatTool;
import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatResponse;
import io.cortavyn.model.api.ToolCall;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class McpToolSourceTest {
    @Test
    void exposesApplicationOwnedMcpResourcesToTheToolLoop() {
        InMemoryWorkspace resources = new InMemoryWorkspace();
        resources.write("guide.txt", "MCP guide").toCompletableFuture().join();
        McpToolSource source = new McpToolSource() {
            @Override public String name() { return "docs"; }
            @Override public List<ChatTool> tools() { return List.of(); }
            @Override public DeepWorkspace resources() { return resources; }
        };
        DeepRun run = DeepAgent.builder(new ScriptedModel()).mcpSources(source).build().invoke("thread-1", "read the guide").toCompletableFuture().join();

        assertEquals("done", run.conversation().messages().getLast().content());
        assertEquals("MCP guide", run.conversation().messages().get(run.conversation().messages().size() - 2).content());
    }

    private static final class ScriptedModel implements ChatModel {
        private int calls;
        @Override public java.util.concurrent.CompletionStage<ChatResponse> complete(io.cortavyn.model.api.ChatRequest request) {
            calls++;
            ChatMessage message = calls == 1
                    ? new ChatMessage(ChatMessageRole.ASSISTANT, "", List.of(), null, List.of(new ToolCall("resource-1", "read_mcp_resource", Map.of("source", "docs", "path", "guide.txt"))), Map.of())
                    : new ChatMessage(ChatMessageRole.ASSISTANT, "done");
            return CompletableFuture.completedFuture(new ChatResponse(message));
        }
    }
}
