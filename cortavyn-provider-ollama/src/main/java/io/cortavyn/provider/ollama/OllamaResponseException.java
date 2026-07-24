package io.cortavyn.provider.ollama;

/** An Ollama response that cannot be represented by Cortavyn's portable chat API. */
public final class OllamaResponseException extends RuntimeException {
    OllamaResponseException(String message) { super(message); }
    OllamaResponseException(String message, Throwable cause) { super(message, cause); }
}
