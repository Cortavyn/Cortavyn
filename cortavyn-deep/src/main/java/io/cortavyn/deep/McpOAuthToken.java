package io.cortavyn.deep;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** OAuth 2.1 token material owned by an application-provided token store. */
public record McpOAuthToken(String accessToken, @Nullable String refreshToken, Instant expiresAt) {
    public McpOAuthToken { if (accessToken == null || accessToken.isBlank()) throw new IllegalArgumentException("accessToken must not be blank"); if (expiresAt == null) throw new IllegalArgumentException("expiresAt must not be null"); }
    public boolean expiresSoon() { return !expiresAt.isAfter(Instant.now().plusSeconds(30)); }
}
