package io.cortavyn.provider.ollama;

/** An unsuccessful HTTP response from Ollama. */
public final class OllamaHttpException extends RuntimeException {
    private final int statusCode;
    OllamaHttpException(int statusCode, String responseBody) { super("Ollama API request failed with status " + statusCode + ": " + responseBody); this.statusCode = statusCode; }
    public int statusCode() { return statusCode; }
}
