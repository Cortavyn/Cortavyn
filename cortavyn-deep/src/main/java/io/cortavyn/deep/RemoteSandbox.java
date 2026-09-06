package io.cortavyn.deep;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Remote sandbox adapter for the Cortavyn sandbox HTTP protocol, including scoped file transfer. */
public final class RemoteSandbox implements Sandbox, SandboxFiles {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client; private final URI endpoint; private final String sandboxId; private final java.util.function.Supplier<CompletionStage<String>> token;
    public RemoteSandbox(HttpClient client, URI endpoint, String sandboxId, java.util.function.Supplier<CompletionStage<String>> token) { this.client = Objects.requireNonNull(client, "client must not be null"); this.endpoint = Objects.requireNonNull(endpoint, "endpoint must not be null"); if (sandboxId == null || sandboxId.isBlank()) throw new IllegalArgumentException("sandboxId must not be blank"); this.sandboxId = sandboxId; this.token = Objects.requireNonNull(token, "token must not be null"); }
    @Override public CompletionStage<SandboxResult> execute(List<String> command, Duration timeout) { if (command.isEmpty()) return java.util.concurrent.CompletableFuture.failedStage(new IllegalArgumentException("command must not be empty")); return post("execute", java.util.Map.of("command", command, "timeoutMillis", timeout.toMillis())).thenApply(json -> new SandboxResult(json.path("exitCode").asInt(), json.path("stdout").asText(), json.path("stderr").asText())); }
    @Override public CompletionStage<Void> putFile(String path, byte[] content) { return post("files", java.util.Map.of("path", safe(path), "contentBase64", Base64.getEncoder().encodeToString(content))).thenApply(ignored -> null); }
    @Override public CompletionStage<byte[]> getFile(String path) { return get("files?path=" + java.net.URLEncoder.encode(safe(path), java.nio.charset.StandardCharsets.UTF_8)).thenApply(json -> Base64.getDecoder().decode(json.path("contentBase64").asText())); }
    private CompletionStage<JsonNode> post(String suffix, Object body) { return token.get().thenCompose(access -> client.sendAsync(request(suffix, HttpRequest.BodyPublishers.ofString(JSON.valueToTree(body).toString()), access, "POST"), HttpResponse.BodyHandlers.ofString()).thenApply(this::json)); }
    private CompletionStage<JsonNode> get(String suffix) { return token.get().thenCompose(access -> client.sendAsync(request(suffix, HttpRequest.BodyPublishers.noBody(), access, "GET"), HttpResponse.BodyHandlers.ofString()).thenApply(this::json)); }
    private HttpRequest request(String suffix, HttpRequest.BodyPublisher body, String access, String method) { HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint.resolve("/v1/sandboxes/" + sandboxId + "/" + suffix)).header("Authorization", "Bearer " + access).header("Content-Type", "application/json"); return "POST".equals(method) ? builder.POST(body).build() : builder.GET().build(); }
    private JsonNode json(HttpResponse<String> response) { if (response.statusCode() / 100 != 2) throw new IllegalStateException("remote sandbox request failed: " + response.statusCode()); try { return JSON.readTree(response.body()); } catch (java.io.IOException failure) { throw new IllegalStateException("invalid remote sandbox response", failure); } }
    private static String safe(String path) { if (path == null || path.isBlank() || path.startsWith("/") || path.contains("..")) throw new IllegalArgumentException("path must be a safe sandbox-relative path"); return path; }
}
