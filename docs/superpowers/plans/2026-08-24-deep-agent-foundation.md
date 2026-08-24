# Deep-Agent Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the shared workspace, request, context, profile, and discovery foundations required by the remaining Deep-Agent parity work.

**Architecture:** New capability records preserve the existing `DeepWorkspace` text API while `DeepWorkspaceFiles` supplies richer reads. `DeepAgent` selects optional capabilities at runtime, so existing custom workspaces remain source-compatible. Context sizing is delegated to `TokenEstimator`; a profile resolves defaults without hiding caller overrides.

**Tech Stack:** Java 25, Maven, JSpecify/NullAway, existing `cortavyn-deep` and `cortavyn-model-api` contracts.

**Spec:** `docs/superpowers/specs/2026-08-24-deep-agent-parity-design.md`

## Global Constraints

- Preserve existing public constructors and `DeepWorkspace` methods.
- Use `CompletionStage` for I/O boundaries.
- Add one focused executable example and one separate commit per task.
- Default Maven tests must not require Docker, E2B credentials, an OAuth browser, or a live MCP server.

---

### Task 1: Rich workspace read contract

**Files:**
- Create: `cortavyn-deep/src/main/java/io/cortavyn/deep/WorkspaceRead.java`
- Create: `cortavyn-deep/src/main/java/io/cortavyn/deep/DeepWorkspaceFiles.java`
- Modify: `InMemoryWorkspace.java`, `FilesystemWorkspace.java`, `DeepTools.java`
- Test: `cortavyn-deep/src/test/java/io/cortavyn/deep/WorkspaceReadTest.java`

**Interfaces:**
- Produces `CompletionStage<WorkspaceRead> readFile(String path, int offset, int limit)`.
- `WorkspaceRead` contains `List<ChatContent> content`, `int offset`, `boolean truncated`, and `long size`.

- [ ] Write a failing test that reads lines two through three from a four-line text file and asserts `truncated`.
- [ ] Implement `WorkspaceRead` and `DeepWorkspaceFiles`; have in-memory and filesystem workspaces return `TextContent` with a deterministic line range.
- [ ] Extend `read_file` with nullable `offset`/`limit` fields while retaining the old whole-text read when both are absent.
- [ ] Run `mvn -pl cortavyn-deep -am -Dtest=WorkspaceReadTest test` and commit `feat(deep): add bounded workspace reads`.

### Task 2: Multimodal workspace files and request content

**Files:**
- Modify: `WorkspaceRead.java`, `FilesystemWorkspace.java`, `DeepRequest.java`, `DeepAgent.java`, `DeepTools.java`
- Create: `cortavyn-deep/src/test/java/io/cortavyn/deep/MultimodalWorkspaceTest.java`
- Create: `examples/deep/src/main/java/io/cortavyn/examples/deep/MultimodalWorkspaceExample.java`

**Interfaces:**
- `DeepRequest(String threadId, List<ChatContent> input)` supplements `DeepRequest(String, String)`.
- Files with known image/audio/video/document MIME types become the corresponding portable `ChatContent`; unknown binaries fail safely.

- [ ] Write tests for PNG and PDF reads with data URIs and for the existing text constructor.
- [ ] Implement extension-to-MIME mapping and bounded base64 conversion in `FilesystemWorkspace`.
- [ ] Start `DeepAgent` with a user `ChatMessage` containing `DeepRequest.input()` content blocks.
- [ ] Run the focused tests and `mvn -pl examples/deep -am package -Prun-multimodal-workspace-example`; commit `feat(deep): support multimodal workspace and input content`.

### Task 3: Routing and policy backends

**Files:**
- Create: `WorkspacePolicy.java`, `CompositeWorkspace.java`, `StoreWorkspace.java`
- Test: `CompositeWorkspaceTest.java`
- Create: `examples/deep/src/main/java/io/cortavyn/examples/deep/CompositeWorkspaceExample.java`

**Interfaces:**
- `WorkspacePolicy.check(Operation operation, String path)` returns a failed stage for denied access.
- `CompositeWorkspace.route(String path)` selects the first declared route; `StoreWorkspace` persists text through a supplied key-value `DeepWorkspaceStore`.

- [ ] Write tests proving first-match route selection and a denied `.env` read.
- [ ] Implement policy-wrapped read/write/list/glob/grep delegation and a route table with safe prefixes.
- [ ] Run focused tests/example; commit `feat(deep): add composite workspace routing and policy hooks`.

### Task 4: Token-aware context policy and offload previews

**Files:**
- Create: `TokenEstimator.java`, `ModelContextWindow.java`
- Modify: `ContextPolicy.java`, `DeepAgent.java`, `DeepEvent.java`
- Test: `TokenContextPolicyTest.java`
- Create: `examples/deep/src/main/java/io/cortavyn/examples/deep/ContextOffloadExample.java`

**Interfaces:**
- `TokenEstimator.estimate(List<ChatMessage>)` returns an integer token estimate.
- `ContextPolicy` gains `maxInputTokens`, `reservedOutputTokens`, and `maxOffloadPreviewCharacters` while retaining its three-argument constructor as a compatibility adapter.

- [ ] Write a test that forces an oversized tool result and asserts the emitted offload path, preview, and post-compaction token budget.
- [ ] Compact at the configured token threshold; retry once after a provider context-overflow classification using a summary-only history.
- [ ] Run focused tests/example; commit `feat(deep): add token-aware context offloading`.

### Task 5: Profiles, skills, and file memory

**Files:**
- Create: `HarnessProfile.java`, `HarnessProfiles.java`, `FileDeepMemory.java`, `MemoryCatalog.java`
- Modify: `DeepAgent.java`, `SkillCatalog.java`
- Test: `HarnessProfileTest.java`, `MemoryCatalogTest.java`
- Create: `examples/deep/src/main/java/io/cortavyn/examples/deep/ProfileAndMemoryExample.java`

**Interfaces:**
- `HarnessProfile` specifies excluded built-in tool names, default context/approval policy, and planning enablement.
- `MemoryCatalog.load(Path root)` combines ordered `AGENTS.md` files; `FileDeepMemory` persists a namespace below a safe root.

- [ ] Write tests for first-profile tool exclusion, nested `SKILL.md` resources, ordered `AGENTS.md`, and namespace traversal rejection.
- [ ] Apply profiles after caller tools and before model requests; preserve caller-supplied Builder settings as higher priority.
- [ ] Run focused tests/example; commit `feat(deep): add harness profiles and file-backed discovery`.

### Task 6: Provider cache markers

**Files:**
- Create: `cortavyn-model-api/src/main/java/io/cortavyn/model/api/PromptCacheHint.java`
- Modify: `ChatMessage.java`, Anthropic and Bedrock request mappers
- Test: provider request serialization tests
- Create: provider examples showing static-system prompt caching

- [ ] Write request-body tests asserting Anthropic `cache_control` and Bedrock cache-point blocks only for static system/memory/skill messages.
- [ ] Add `PromptCacheHint.STATIC` without changing provider-neutral content semantics.
- [ ] Run provider tests/examples; commit `feat(model): add static prompt cache hints`.
