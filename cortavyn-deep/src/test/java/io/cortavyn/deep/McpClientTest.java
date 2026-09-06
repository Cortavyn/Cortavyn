package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cortavyn.model.api.ToolCall;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class McpClientTest {
    @Test
    void discoversAndCallsToolsAndResourcesInOneSession() {
        try (McpClient client = McpClient.connect("test", new FakeTransport()).toCompletableFuture().join()) {
            assertEquals("hello", client.tools().getFirst().executor().execute(new ToolCall("call", "echo", java.util.Map.of("value", "hello"))).toCompletableFuture().join().content());
            assertEquals("resource", client.resources().read("memo://one").toCompletableFuture().join());
        }
    }
    private static final class FakeTransport implements McpTransport {
        private static final ObjectMapper JSON = new ObjectMapper();
        @Override public CompletionStage<JsonNode> request(JsonNode message) {
            String method = message.path("method").asText();
            Object result = switch (method) {
                case "initialize" -> java.util.Map.of("capabilities", java.util.Map.of());
                case "tools/list" -> java.util.Map.of("tools", java.util.List.of(java.util.Map.of("name", "echo", "description", "echoes", "inputSchema", java.util.Map.of("type", "object"))));
                case "resources/list" -> java.util.Map.of("resources", java.util.List.of(java.util.Map.of("uri", "memo://one")));
                case "tools/call" -> java.util.Map.of("content", java.util.List.of(java.util.Map.of("type", "text", "text", "hello")));
                case "resources/read" -> java.util.Map.of("contents", java.util.List.of(java.util.Map.of("type", "text", "text", "resource")));
                default -> throw new AssertionError(method);
            };
            return CompletableFuture.completedFuture(JSON.valueToTree(java.util.Map.of("jsonrpc", "2.0", "id", message.path("id").asLong(), "result", result)));
        }
        @Override public void close() { }
    }
}
