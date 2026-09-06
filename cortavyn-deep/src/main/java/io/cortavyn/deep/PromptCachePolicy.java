package io.cortavyn.deep;

/** Controls cache markers for static Deep Agent prompt sections. */
public record PromptCachePolicy(boolean enabled) { public static PromptCachePolicy defaults() { return new PromptCachePolicy(true); } public static PromptCachePolicy disabled() { return new PromptCachePolicy(false); } }
