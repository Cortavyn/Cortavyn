package io.cortavyn.model.api;
/** A typed part of a chat message, enabling multimodal requests and reasoning output. */
public sealed interface ChatContent permits TextContent, ImageContent, AudioContent, DocumentContent, ReasoningContent { }
