package io.cortavyn.provider.gemini;

/** An unsuccessful HTTP response from the Gemini API. */
public final class GeminiHttpException extends RuntimeException {
    private final int statusCode;

    GeminiHttpException(int statusCode, String responseBody) {
        super("Gemini API request failed with status " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
