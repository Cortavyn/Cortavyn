# Chat Model Roadmap

This document tracks the remaining work needed to make Cortavyn's chat-model layer comparable to the relevant LangChain capabilities. It distinguishes portable API contracts from provider implementations: a checked item must work end-to-end with the named providers and have focused tests.

## Current foundation

- [x] Maven multi-module design with one optional artifact per provider
- [x] Java 25 and JSpecify/NullAway enforcement
- [x] Portable messages, text, reasoning, image, audio, and document content contracts
- [x] Portable tool definitions, tool calls, tool results, token usage, and response metadata
- [x] Reasoning extraction and provider-state preservation for OpenAI Responses, Anthropic, Gemini, Bedrock, DeepSeek, Mistral, Azure OpenAI, Ollama, OpenRouter, and compatible APIs
- [x] OpenAI Responses conversation continuation with automatic `previous_response_id`
- [x] Native OpenAI Responses function tools
- [x] Gemini thought-signature preservation, including function-call parts

## P0 — Reliable tool-using conversations

### Provider tool-call round trip

**Goal:** A caller can append an assistant response containing tool calls and then tool-result messages to a conversation without losing provider-required call IDs, arguments, signatures, or reasoning state.

- [x] Gemini
- [x] OpenAI Responses API
- [x] OpenAI Chat Completions
- [x] Anthropic Messages
- [x] AWS Bedrock Converse
- [x] Azure OpenAI Chat Completions
- [x] Mistral Chat Completions
- [x] Ollama Chat API
- [x] OpenRouter
- [x] OpenAI-compatible core (Groq, DeepSeek, xAI, Moonshot/Kimi, Qwen, Cloudflare Workers AI, vLLM, Vercel AI Gateway)

**Acceptance criteria**

1. An adapter serializes assistant tool calls as well as tool results.
2. A two-step fake HTTP/provider test verifies model tool call -> application tool result -> final model response.
3. Provider-specific reasoning/signature state survives the same loop where required.

### LangChain-style chat agent

**Goal:** Add a public `ChatAgent` in `cortavyn-chat`, comparable to LangChain's `createAgent({ model, tools })`. It wraps an existing `ChatModel`, executes application-owned tools, and continues the conversation until the model returns a final assistant response. Graph and deep-agent integrations should reuse this execution core rather than introduce a second tool loop.

- [x] `ChatAgent` implements `ChatSession`
- [x] Tool registry and lookup by tool name
- [x] Parallel execution of calls in one model response
- [x] Maximum iteration limit
- [x] Unknown tool and tool-failure result policy
- [x] Integration test using a scripted chat model
- [ ] `ToolRuntime`: run context, user context, persistent store, and progress writer
- [ ] Structured and multimodal tool results
- [ ] Graph `ToolNode` and state-update commands

## P1 — Complete chat-model interaction surface

### Streaming

`StreamingChatModel` and stream event types exist, but no adapter implements them.

- [ ] Text deltas
- [ ] Tool-call argument deltas
- [ ] Reasoning deltas where supported
- [ ] Final usage and finish metadata
- [ ] OpenAI, Anthropic, Gemini, Bedrock, Mistral, Ollama, and compatible-provider implementations
- [ ] Cancellation and back-pressure tests

### Structured output

`StructuredOutputChatModel` and `StructuredOutputSchema` exist, but no adapter implements provider-enforced schema output.

- [ ] OpenAI/Azure Responses or JSON Schema output
- [ ] Anthropic strict tool-schema strategy
- [ ] Gemini response schema and MIME type strategy
- [ ] Mistral, Groq, xAI, and compatible-provider JSON modes where available
- [ ] Portable fallback via a synthetic tool call
- [ ] JSON parsing and schema-validation failure model

### Multimodal request mapping

The portable content types exist but are not yet mapped consistently to provider wire formats.

- [ ] Images: OpenAI, Anthropic, Gemini, Bedrock, OpenRouter, Ollama where supported
- [ ] Audio: OpenAI and Gemini where supported
- [ ] Documents/PDFs: OpenAI, Anthropic, Gemini, Bedrock where supported
- [ ] Capability checks and clear errors for unsupported content types

## P2 — Provider-native agent capabilities

- [ ] OpenAI Responses built-in tools: web search, file search, code interpreter, computer use, image generation, remote MCP
- [ ] Provider-native citations, annotations, and source metadata
- [ ] Provider-native tool-choice and parallel-tool-call configuration
- [ ] Provider-native safety/refusal metadata
- [ ] OpenAI/Azure response-format, logprobs, and predicted-output support where applicable

## P3 — Runtime and operational capabilities

- [ ] Model capability/profile registry: modalities, tool support, structured output, streaming, context window, and reasoning support
- [ ] Unified provider/model factory and runtime model selection
- [ ] Batch invocation and bounded concurrency
- [ ] Retry, backoff, rate limiting, and fallback model policies
- [ ] Prompt/response cache abstraction
- [ ] Callbacks, tracing, metrics, token/cost accounting, and OpenTelemetry/LangSmith-compatible export
- [ ] Fake/scripted model implementations for unit and integration tests

## Implementation order

1. Finish provider tool-call round trips.
2. Implement `ToolCallingChatAgent` and its failure/limit policies.
3. Add streaming, starting with OpenAI and Anthropic.
4. Implement structured output.
5. Map multimodal content and provider-native tools.
6. Add profiles, resilience, observability, batching, and caching.

## References

- [LangChain BaseChatModel capabilities](https://reference.langchain.com/python/langchain-core/language_models/chat_models/BaseChatModel)
- [LangChain chat-model integrations](https://docs.langchain.com/oss/python/integrations/chat/index)
- [LangChain structured output](https://docs.langchain.com/oss/python/langchain/structured-output)
