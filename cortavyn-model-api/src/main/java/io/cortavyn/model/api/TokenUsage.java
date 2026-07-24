package io.cortavyn.model.api;
/** Token accounting supplied by a provider. */
public record TokenUsage(int inputTokens, int outputTokens, int totalTokens) { public TokenUsage { if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0) throw new IllegalArgumentException("token counts must not be negative"); } }
