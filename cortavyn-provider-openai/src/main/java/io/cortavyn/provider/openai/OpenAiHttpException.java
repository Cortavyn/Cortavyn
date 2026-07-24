package io.cortavyn.provider.openai;

/** An unsuccessful response from the OpenAI HTTP API. */
public final class OpenAiHttpException extends RuntimeException {
    private final int statusCode;

    OpenAiHttpException(int statusCode, String responseBody) {
        super("OpenAI request failed with HTTP " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
