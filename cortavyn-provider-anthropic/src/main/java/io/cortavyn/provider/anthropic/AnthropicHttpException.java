package io.cortavyn.provider.anthropic;

/** An unsuccessful HTTP response from the Anthropic Messages API. */
public final class AnthropicHttpException extends RuntimeException {
    private final int statusCode;
    AnthropicHttpException(int statusCode, String responseBody) {
        super("Anthropic API request failed with status " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
    }
    public int statusCode() { return statusCode; }
}
