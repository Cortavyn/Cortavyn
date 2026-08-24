package io.cortavyn.deep;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** Process-local OAuth token store for examples and tests. */
public final class InMemoryMcpOAuthTokenStore implements McpOAuthTokenStore {
    private final ConcurrentHashMap<String, McpOAuthToken> tokens = new ConcurrentHashMap<>();
    @Override public CompletionStage<Optional<McpOAuthToken>> load(String key) { return CompletableFuture.completedFuture(Optional.ofNullable(tokens.get(key))); }
    @Override public CompletionStage<Void> save(String key, McpOAuthToken token) { tokens.put(key, token); return CompletableFuture.completedFuture(null); }
}
