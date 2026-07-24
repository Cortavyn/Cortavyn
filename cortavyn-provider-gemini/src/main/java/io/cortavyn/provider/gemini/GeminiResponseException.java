package io.cortavyn.provider.gemini;

/** A Gemini response that cannot be represented by Cortavyn's portable chat API. */
public final class GeminiResponseException extends RuntimeException {
    GeminiResponseException(String message) {
        super(message);
    }

    GeminiResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
