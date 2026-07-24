package io.cortavyn.provider.openrouter;

/** An unsuccessful HTTP response from OpenRouter. */
public final class OpenRouterHttpException extends RuntimeException {
    private final int statusCode;

    OpenRouterHttpException(int statusCode, String responseBody) {
        super("OpenRouter API request failed with status " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
    }

    public int statusCode() { return statusCode; }
}
