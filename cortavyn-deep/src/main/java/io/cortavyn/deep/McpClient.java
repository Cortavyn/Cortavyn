package io.cortavyn.deep;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cortavyn.chat.ChatTool;
import io.cortavyn.chat.ToolExecutionResult;
import io.cortavyn.model.api.ChatContent;
import io.cortavyn.model.api.TextContent;
import io.cortavyn.model.api.ToolDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

/** Functional MCP client: initializes a session, discovers tools, invokes them, and closes its transport. */
public final class McpClient implements McpToolSource, AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String name; private final McpTransport transport; private final AtomicLong ids = new AtomicLong(); private final List<ChatTool> tools; private final DeepWorkspace resources;
    private McpClient(String name, McpTransport transport, List<ChatTool> tools, DeepWorkspace resources) { this.name = name; this.transport = transport; this.tools = List.copyOf(tools); this.resources = resources; }
    public static CompletionStage<McpClient> connect(String name, McpTransport transport) {
        Objects.requireNonNull(name, "name must not be null"); Objects.requireNonNull(transport, "transport must not be null");
        AtomicLong ids = new AtomicLong();
        return request(transport, ids, "initialize", Map.of("protocolVersion", "2025-03-26", "capabilities", Map.of(), "clientInfo", Map.of("name", "cortavyn", "version", "0.1"))).thenCompose(ignored -> request(transport, ids, "tools/list", Map.of()).thenCombine(request(transport, ids, "resources/list", Map.of()).exceptionally(failure -> JSON.createObjectNode()), (toolsResponse, resourcesResponse) -> {
            List<ChatTool> tools = new ArrayList<>();
            for (JsonNode tool : toolsResponse.path("result").path("tools")) {
                String toolName = tool.path("name").asText(); String description = tool.path("description").asText("");
                @SuppressWarnings("unchecked") Map<String, Object> schema = JSON.convertValue(tool.path("inputSchema"), Map.class);
                tools.add(ChatTool.withRuntime(new ToolDefinition(toolName, description, schema), (call, runtime) -> request(transport, ids, "tools/call", Map.of("name", toolName, "arguments", call.arguments())).thenApply(result -> ToolExecutionResult.success(content(result.path("result").path("content"))))));
            }
            DeepWorkspace resources = new McpResourceWorkspace(resourcesResponse.path("result").path("resources"), uri -> request(transport, ids, "resources/read", Map.of("uri", uri)).thenApply(result -> content(result.path("result").path("contents"))));
            McpClient client = new McpClient(name, transport, tools, resources); client.ids.set(ids.get()); return client;
        }));
    }
    @Override public String name() { return name; }
    @Override public List<ChatTool> tools() { return tools; }
    @Override public DeepWorkspace resources() { return resources; }
    @Override public void close() { transport.close(); }
    private CompletionStage<JsonNode> request(String method, Map<String, Object> params) { return request(transport, ids, method, params); }
    private static CompletionStage<JsonNode> request(McpTransport transport, AtomicLong ids, String method, Map<String, Object> params) { long id = ids.incrementAndGet(); return transport.request(JSON.valueToTree(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params))).thenApply(response -> { if (response.has("error")) throw new IllegalStateException("MCP " + method + " failed: " + response.path("error").path("message").asText()); return response; }); }
    private static List<ChatContent> content(JsonNode blocks) { List<ChatContent> parsed = new ArrayList<>(); for (JsonNode block : blocks) { if ("text".equals(block.path("type").asText())) parsed.add(new TextContent(block.path("text").asText())); else parsed.add(new TextContent(block.toString())); } return parsed.isEmpty() ? List.of(new TextContent("")) : parsed; }
}
