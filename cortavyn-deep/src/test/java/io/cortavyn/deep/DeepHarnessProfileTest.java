package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.model.api.ChatResponse;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class DeepHarnessProfileTest {
    @Test
    void hidesSelectedBuiltinsButKeepsApplicationTools() {
        CapturingModel model = new CapturingModel();
        DeepHarnessProfile profile = new DeepHarnessProfile(Set.of(DeepHarnessProfile.BuiltIn.WORKSPACE));
        try (DeepAgent agent = DeepAgent.builder(model).harnessProfile(profile).build()) {
            agent.invoke("thread", "hello").toCompletableFuture().join();
        }
        assertTrue(java.util.Objects.requireNonNull(model.request).tools().stream().anyMatch(tool -> tool.name().equals("read_file")));
        assertFalse(java.util.Objects.requireNonNull(model.request).tools().stream().anyMatch(tool -> tool.name().equals("write_todos")));
    }
    private static final class CapturingModel implements ChatModel {
        private @org.jspecify.annotations.Nullable ChatRequest request;
        @Override public java.util.concurrent.CompletionStage<ChatResponse> complete(ChatRequest request) { this.request = request; return CompletableFuture.completedFuture(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, "done"))); }
    }
}
