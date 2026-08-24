package io.cortavyn.deep;

import org.jspecify.annotations.Nullable;

/** Token-aware limits applied to one deep-agent model/tool loop. */
public record ContextPolicy(int maxIterations, int inlineToolResultCharacters, int historyCharacters,
                            int inlineToolResultTokens, int historyTokens,
                            @Nullable Integer contextWindowTokens, int offloadPreviewCharacters) {
    public ContextPolicy {
        if (maxIterations <= 0 || inlineToolResultCharacters <= 0 || historyCharacters <= 0 || inlineToolResultTokens <= 0 || historyTokens <= 0 || offloadPreviewCharacters <= 0) throw new IllegalArgumentException("context limits must be positive");
        if (contextWindowTokens != null && contextWindowTokens <= 0) throw new IllegalArgumentException("contextWindowTokens must be positive");
    }
    /** Compatibility constructor; token values use the conservative 4-character estimate. */
    public ContextPolicy(int maxIterations, int inlineToolResultCharacters, int historyCharacters) { this(maxIterations, inlineToolResultCharacters, historyCharacters, Math.max(1, inlineToolResultCharacters / 4), Math.max(1, historyCharacters / 4), null, 1_000); }
    public static ContextPolicy defaults() { return new ContextPolicy(20, 16_000, 100_000, 4_000, 25_000, null, 1_000); }
    int effectiveHistoryTokens() { return contextWindowTokens == null ? historyTokens : Math.min(historyTokens, Math.max(1, contextWindowTokens - 4_096)); }
}
