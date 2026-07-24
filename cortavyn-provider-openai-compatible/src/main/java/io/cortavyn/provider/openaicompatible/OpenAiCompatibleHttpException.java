package io.cortavyn.provider.openaicompatible;
/** A non-success response from an OpenAI-compatible endpoint. */
public final class OpenAiCompatibleHttpException extends RuntimeException { private final int statusCode; OpenAiCompatibleHttpException(int statusCode, String body) { super("Compatible endpoint request failed with status " + statusCode + ": " + body); this.statusCode = statusCode; } public int statusCode() { return statusCode; } }
