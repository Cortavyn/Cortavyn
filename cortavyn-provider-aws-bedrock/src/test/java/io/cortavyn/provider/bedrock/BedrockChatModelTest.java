package io.cortavyn.provider.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.model.api.DocumentContent;
import io.cortavyn.model.api.ImageContent;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

@org.jspecify.annotations.NullMarked
class BedrockChatModelTest {
    @Test
    void mapsSystemMessagesAndConversationMessagesForConverse() {
        try (BedrockChatModel model = BedrockChatModel.builder()
                .region(Region.US_EAST_1)
                .modelId("anthropic.claude-test")
                .build()) {
            var request = model.toConverseRequest(new ChatRequest(List.of(
                    new ChatMessage(ChatMessageRole.SYSTEM, "Be concise."),
                    new ChatMessage(ChatMessageRole.USER, "Hello"))));
            assertEquals("anthropic.claude-test", request.modelId());
            assertEquals("Be concise.", request.system().getFirst().text());
            assertEquals("Hello", request.messages().getFirst().content().getFirst().text());
        }
    }

    @Test
    void mapsBase64ImagesAndDocumentsToConverseBlocks() {
        try (BedrockChatModel model = BedrockChatModel.builder().region(Region.US_EAST_1).modelId("anthropic.claude-test").build()) {
            var request = model.toConverseRequest(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, List.of(
                    new ImageContent(URI.create("data:image/png;base64,aW1hZ2U="), "image/png"),
                    new DocumentContent(URI.create("data:application/pdf;base64,cGRm"), "application/pdf", "report.pdf"))))));
            assertEquals("png", request.messages().getFirst().content().getFirst().image().formatAsString());
            assertEquals("pdf", request.messages().getFirst().content().get(1).document().formatAsString());
            assertEquals("report.pdf", request.messages().getFirst().content().get(1).document().name());
        }
    }
}
