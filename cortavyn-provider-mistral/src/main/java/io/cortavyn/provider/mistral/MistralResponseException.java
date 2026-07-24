package io.cortavyn.provider.mistral;

/** A Mistral response could not be mapped to Cortavyn's portable response contract. */
public final class MistralResponseException extends RuntimeException {
    MistralResponseException(String message) { super(message); }
    MistralResponseException(String message, Throwable cause) { super(message, cause); }
}
