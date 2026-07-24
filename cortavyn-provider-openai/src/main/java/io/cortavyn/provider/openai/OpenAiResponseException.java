package io.cortavyn.provider.openai;

/** An OpenAI response could not be mapped to Cortavyn's portable response contract. */
public final class OpenAiResponseException extends RuntimeException {
    OpenAiResponseException(String message) {
        super(message);
    }

    OpenAiResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
