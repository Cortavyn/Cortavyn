package io.cortavyn.provider.openaicompatible;
/** An OpenAI-compatible response that cannot be mapped to the portable chat API. */
public final class OpenAiCompatibleResponseException extends RuntimeException { OpenAiCompatibleResponseException(String message) { super(message); } OpenAiCompatibleResponseException(String message, Throwable cause) { super(message, cause); } }
