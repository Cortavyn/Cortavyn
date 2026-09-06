package io.cortavyn.deep;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** OAuth 2.1 client-credentials, authorization-code exchange, and refresh support for MCP HTTP sessions. */
public final class McpOAuth {
    private static final ObjectMapper JSON = new ObjectMapper();
    private McpOAuth() { }
    public record Configuration(URI authorizationEndpoint, URI tokenEndpoint, String clientId, String clientSecret, String scope) { public Configuration { Objects.requireNonNull(authorizationEndpoint, "authorizationEndpoint must not be null"); Objects.requireNonNull(tokenEndpoint, "tokenEndpoint must not be null"); if (clientId == null || clientId.isBlank()) throw new IllegalArgumentException("clientId must not be blank"); Objects.requireNonNull(clientSecret, "clientSecret must not be null"); Objects.requireNonNull(scope, "scope must not be null"); } }
    public static URI authorizationUri(Configuration configuration, URI redirectUri, String state, String codeChallenge) { return URI.create(configuration.authorizationEndpoint() + "?response_type=code&client_id=" + encode(configuration.clientId()) + "&redirect_uri=" + encode(redirectUri.toString()) + "&scope=" + encode(configuration.scope()) + "&state=" + encode(state) + "&code_challenge=" + encode(codeChallenge) + "&code_challenge_method=S256"); }
    public static CompletionStage<McpOAuthToken> exchangeAuthorizationCode(HttpClient client, Configuration configuration, String code, URI redirectUri, String codeVerifier) { return token(client, configuration, Map.of("grant_type", "authorization_code", "code", code, "redirect_uri", redirectUri.toString(), "code_verifier", codeVerifier)); }
    public static CompletionStage<McpOAuthToken> clientCredentials(HttpClient client, Configuration configuration) { return token(client, configuration, Map.of("grant_type", "client_credentials", "scope", configuration.scope())); }
    public static CompletionStage<McpOAuthToken> refresh(HttpClient client, Configuration configuration, String refreshToken) { return token(client, configuration, Map.of("grant_type", "refresh_token", "refresh_token", refreshToken)); }
    public static java.util.function.Supplier<CompletionStage<String>> bearerSupplier(HttpClient client, Configuration configuration, McpOAuthTokenStore store, String key) { return () -> store.load(key).thenCompose(found -> { if (found.isPresent() && !found.get().expiresSoon()) return java.util.concurrent.CompletableFuture.completedFuture(found.get().accessToken()); CompletionStage<McpOAuthToken> next = found.isPresent() && found.get().refreshToken() != null ? refresh(client, configuration, found.get().refreshToken()) : clientCredentials(client, configuration); return next.thenCompose(token -> store.save(key, token).thenApply(ignored -> token.accessToken())); }); }
    private static CompletionStage<McpOAuthToken> token(HttpClient client, Configuration configuration, Map<String, String> values) { String form = values.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue())).collect(java.util.stream.Collectors.joining("&")); String authorization = java.util.Base64.getEncoder().encodeToString((configuration.clientId() + ":" + configuration.clientSecret()).getBytes(StandardCharsets.UTF_8)); HttpRequest request = HttpRequest.newBuilder(configuration.tokenEndpoint()).header("Content-Type", "application/x-www-form-urlencoded").header("Authorization", "Basic " + authorization).POST(HttpRequest.BodyPublishers.ofString(form)).build(); return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> { if (response.statusCode() / 100 != 2) throw new IllegalStateException("OAuth token request failed: " + response.statusCode()); try { JsonNode json = JSON.readTree(response.body()); return new McpOAuthToken(json.path("access_token").asText(), json.path("refresh_token").isMissingNode() ? null : json.path("refresh_token").asText(), Instant.now().plusSeconds(json.path("expires_in").asLong(3600))); } catch (java.io.IOException failure) { throw new IllegalStateException("invalid OAuth token response", failure); } }); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
