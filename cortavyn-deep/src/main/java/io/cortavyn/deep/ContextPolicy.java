package io.cortavyn.deep;
/** Limits applied to one deep-agent model/tool loop. */
public record ContextPolicy(int maxIterations, int inlineToolResultCharacters, int historyCharacters) { public ContextPolicy { if (maxIterations <= 0 || inlineToolResultCharacters <= 0 || historyCharacters <= 0) throw new IllegalArgumentException("context limits must be positive"); } public static ContextPolicy defaults() { return new ContextPolicy(20, 16_000, 100_000); } }
