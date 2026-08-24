# Deep-Agent Parity Design

## Goal

Bring `cortavyn-deep` to feature parity with the Deep Agents execution environment where parity is portable, while providing production-ready Docker, E2B, MCP transport, and OAuth adapters.

## Decisions

- Keep `cortavyn-deep` provider-neutral. Provider-specific prompt-cache mapping belongs in the Anthropic and Bedrock adapters, behind portable static-prompt metadata.
- Extend the workspace through backwards-compatible capability interfaces instead of breaking `DeepWorkspace` implementations.
- Model multimodal file reads as portable `ChatContent` blocks and evolve `DeepRequest` from text-only input to a message/content form while retaining the existing string constructor.
- Use a token estimator plus declared model-context window. Exact provider tokenization remains optional; agents reserve a configurable completion budget and retry once after a context-overflow response using stronger compaction.
- Make offloaded tool data durable workspace files with a deterministic preview, byte/character count, and an explicit read reference.
- Add immutable `HarnessProfile` objects that control built-in tools, planning defaults, context policy, and approval defaults. Model lookup selects a profile when configured; callers can override it.
- Add filesystem and store discovery adapters for `SKILL.md` and `AGENTS.md`, preserving safe relative resource paths and exposing writeable file-backed memory.
- Add `CompositeWorkspace` routing and `WorkspacePolicy` hooks. `read_file` gains line/byte offset and limit while legacy `read()` remains available.
- Implement MCP as transport-neutral sessions: stdio JSON-RPC and Streamable HTTP clients discover tools/resources, expose them as `ChatTool`s, and own lifecycle. OAuth 2.1 uses authorization-code PKCE, RFC 8414 metadata discovery, RFC 7591 dynamic client registration, refresh-token rotation, and a caller-provided browser/callback bridge.
- Implement `DockerSandbox` with a hardened `docker run` command and file copy, and `E2bSandbox` over the E2B REST API. Both enforce timeout, CPU/memory/network/mount limits and expose upload/download operations through one sandbox contract.
- Add a default `general-purpose` subagent, runtime-created specialists, and child stream handles. Existing synchronous and asynchronous task APIs remain compatible.
- Keep `DeepInterpreter` host-free; add optional programmatic tool calls only through an explicit allowlist and structured request/response bridge.

## Delivery Order

1. Workspace capabilities, multimodal content, offset/limit, routing, policy hooks, and examples.
2. Token-aware context engineering and context-overflow recovery.
3. Profiles and provider prompt-cache metadata/mapping.
4. Skills/memory discovery and file-backed memory.
5. MCP session, stdio/HTTP, and OAuth 2.1.
6. Docker and E2B sandboxes with transfer APIs.
7. General-purpose/dynamic subagents and nested streams.
8. Interpreter tool allowlist and programmatic tool calling.

## Compatibility and Safety

All new APIs must retain the existing `DeepWorkspace`, `DeepRequest`, `Sandbox`, `DeepInterpreter`, and `DeepAgent.Builder` entry points. No tool becomes visible without explicit configuration or a profile opt-in where exposure is sensitive. Network/OAuth credentials stay caller-owned and are never serialized into checkpoints.

## Verification

Every delivery unit includes focused unit/integration tests, one provider-neutral executable example, and a dedicated commit. Docker/E2B/MCP transport tests use local fake servers or command shims; optional live smoke tests are profile-gated and never run by the default Maven build.
