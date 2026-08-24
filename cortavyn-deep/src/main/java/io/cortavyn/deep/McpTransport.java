package io.cortavyn.deep;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.CompletionStage;

/** JSON-RPC transport used by a lifecycle-managed MCP client session. */
public interface McpTransport extends AutoCloseable {
    CompletionStage<JsonNode> request(JsonNode message);
    @Override void close();
}
