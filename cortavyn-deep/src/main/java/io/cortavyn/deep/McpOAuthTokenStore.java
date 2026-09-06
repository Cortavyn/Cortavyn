package io.cortavyn.deep;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Durable application hook for MCP OAuth tokens; never expose its values to model tools. */
public interface McpOAuthTokenStore {
    CompletionStage<Optional<McpOAuthToken>> load(String key);
    CompletionStage<Void> save(String key, McpOAuthToken token);
}
