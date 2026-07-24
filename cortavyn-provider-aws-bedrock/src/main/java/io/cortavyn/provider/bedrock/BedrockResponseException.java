package io.cortavyn.provider.bedrock;

/** Indicates that a Bedrock Converse response cannot be represented by the portable chat API. */
public final class BedrockResponseException extends RuntimeException {
    BedrockResponseException(String message) { super(message); }
}
