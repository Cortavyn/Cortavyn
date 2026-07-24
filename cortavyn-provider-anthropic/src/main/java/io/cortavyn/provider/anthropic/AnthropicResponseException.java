package io.cortavyn.provider.anthropic;

/** An Anthropic response that cannot be represented by Cortavyn's portable chat API. */
public final class AnthropicResponseException extends RuntimeException {
    AnthropicResponseException(String message) { super(message); }
    AnthropicResponseException(String message, Throwable cause) { super(message, cause); }
}
