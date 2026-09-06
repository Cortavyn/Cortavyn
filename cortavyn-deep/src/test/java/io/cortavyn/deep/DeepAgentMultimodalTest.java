package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.model.api.ChatResponse;
import io.cortavyn.model.api.ImageContent;
import io.cortavyn.model.api.TextContent;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DeepAgentMultimodalTest {
    @Test
    void acceptsMultimodalRequestBlocks() {
        CapturingModel model = new CapturingModel();
        try (DeepAgent agent = DeepAgent.builder(model).build()) {
            agent.invoke(new DeepRequest("thread", List.of(new TextContent("describe this"), new ImageContent(URI.create("data:image/png;base64,AA=="), "image/png")))).toCompletableFuture().join();
        }

        ChatMessage user = java.util.Objects.requireNonNull(model.request).messages().getLast();
        assertEquals(ChatMessageRole.USER, user.role());
        assertEquals(2, user.contentBlocks().size());
        assertInstanceOf(ImageContent.class, user.contentBlocks().get(1));
    }

    private static final class CapturingModel implements ChatModel {
        private @Nullable ChatRequest request;
        @Override public java.util.concurrent.CompletionStage<ChatResponse> complete(ChatRequest value) {
            request = value;
            return CompletableFuture.completedFuture(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, "done")));
        }
    }
}
