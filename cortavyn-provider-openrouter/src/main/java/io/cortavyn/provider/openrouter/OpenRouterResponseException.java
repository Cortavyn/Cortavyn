package io.cortavyn.provider.openrouter;

/** An OpenRouter response that cannot be represented by Cortavyn's portable chat API. */
public final class OpenRouterResponseException extends RuntimeException {
    OpenRouterResponseException(String message) { super(message); }
    OpenRouterResponseException(String message, Throwable cause) { super(message, cause); }
}
