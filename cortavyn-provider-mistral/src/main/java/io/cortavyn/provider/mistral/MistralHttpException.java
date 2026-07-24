package io.cortavyn.provider.mistral;

/** An unsuccessful response from the Mistral HTTP API. */
public final class MistralHttpException extends RuntimeException {
    private final int statusCode;

    MistralHttpException(int statusCode, String responseBody) {
        super("Mistral request failed with HTTP " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
    }

    public int statusCode() { return statusCode; }
}
