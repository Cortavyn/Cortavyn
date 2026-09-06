package io.cortavyn.deep;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

/** Streamable-HTTP MCP transport with an optional OAuth bearer-token supplier. */
public final class HttpMcpTransport implements McpTransport {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client; private final URI endpoint; private final java.util.function.Supplier<CompletionStage<String>> token; private final AtomicReference<String> sessionId = new AtomicReference<>();
    public HttpMcpTransport(HttpClient client, URI endpoint, java.util.function.Supplier<CompletionStage<String>> token) { this.client = Objects.requireNonNull(client, "client must not be null"); this.endpoint = Objects.requireNonNull(endpoint, "endpoint must not be null"); this.token = Objects.requireNonNull(token, "token must not be null"); }
    @Override public CompletionStage<JsonNode> request(JsonNode message) { return token.get().thenCompose(accessToken -> { HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream").POST(HttpRequest.BodyPublishers.ofString(message.toString())); if (!accessToken.isBlank()) builder.header("Authorization", "Bearer " + accessToken); String session = sessionId.get(); if (session != null) builder.header("Mcp-Session-Id", session); return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString()).thenApply(response -> { if (response.statusCode() / 100 != 2) throw new IllegalStateException("MCP HTTP request failed: " + response.statusCode()); response.headers().firstValue("Mcp-Session-Id").ifPresent(sessionId::set); try { String body = response.body(); if (body.startsWith("event:")) body = body.lines().filter(line -> line.startsWith("data:")).map(line -> line.substring(5).strip()).findFirst().orElseThrow(() -> new IllegalStateException("MCP SSE response omitted data")); return JSON.readTree(body); } catch (java.io.IOException failure) { throw new IllegalStateException("invalid MCP JSON response", failure); } }); }); }
    @Override public void close() { }
}
