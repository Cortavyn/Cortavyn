package io.cortavyn.model.api;
/** An event from a streaming chat completion. */
public sealed interface ChatStreamEvent permits ChatTextDelta, ChatReasoningDelta, ChatToolCallDelta, ChatCompletion { }
