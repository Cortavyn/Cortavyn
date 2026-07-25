# Cortavyn

Durable agent systems for the JVM.

Cortavyn is a Java 25, Maven-based foundation for durable agent runs. Core orchestration, graph execution, chat, deep agents, and model-provider adapters are separate artifacts so applications only depend on the capabilities they use.

All production packages are `@NullMarked` with [JSpecify 1.0.0](https://jspecify.dev/docs/using/): reference types are non-null by default, and optional values are explicitly annotated with `@Nullable`.
`mvn verify` enforces these contracts with NullAway in JSpecify mode; violations and missing explicit package marking fail the build.

## Modules

| Artifact | Purpose |
| --- | --- |
| `cortavyn-core` | Durable run and state contracts |
| `cortavyn-model-api` | Portable chat-model contracts |
| `cortavyn-graph` | Stateful graph runtime, checkpoints, routing, and streaming |
| `cortavyn-chat` | Conversation and chat-session contracts |
| `cortavyn-deep` | Planning and deep-agent contracts |
| `cortavyn-provider-*` | Optional provider integration boundaries |

`graph`, `chat`, and provider modules depend only on the APIs they need. `deep` composes `graph` and `model-api`; no core module depends on a provider SDK.

## Roadmap

[ROADMAP.md](ROADMAP.md) tracks the remaining chat-model capabilities and their implementation status.

## Architecture

The versioned [Structurizr C4 model](architecture/workspace.dsl) documents module responsibilities and dependency directions. The `cortavyn-architecture` module enforces those directions with ArchUnit as part of `mvn test`.
The [agent and tool flow](docs/agent-tool-flow.md) explains the execution loop and runtime ownership with Mermaid diagrams.

## Typed tools

`ChatTool.typed` derives a provider JSON Schema from a Java `record` and converts a tool call back to that type. `@ToolName` and `@ToolDescription` are optional convenience metadata; the explicit overload accepts the name and description directly. `@Nullable` record components are not required by the generated schema.

```java
@ToolName("get_weather")
@ToolDescription("Gets the current weather for a city.")
record WeatherArguments(@ToolDescription("The city to look up.") String city) { }

var weather = ChatTool.typed(WeatherArguments.class, arguments ->
        CompletableFuture.completedFuture(ToolExecutionResult.success(weatherFor(arguments.city()))));
```

Runtime-aware tools additionally receive `ToolRuntime` with application context, an injected `ToolStore`, and a `ToolProgressWriter`. Configure it once on `ChatAgent.builder(model).runtime(runtime)`.

## Graphs

`cortavyn-graph` is a provider-independent, LangGraph-inspired orchestration runtime. Define a `StateSchema`, register asynchronous nodes, connect `START` and `END`, then compile the immutable graph. Nodes return a partial `StateUpdate`; channels decide whether a value is replaced, reduced, collected as a topic, or cleared after a superstep.

```java
var schema = StateSchema.builder(GraphState.adapter())
        .channel("steps", StateChannel.topic())
        .channel("answer", StateChannel.lastValue())
        .build();

var graph = new StateGraph<>(schema)
        .addNode("research", (state, runtime) -> completedFuture(new StateUpdate(Map.of("steps", "research"))))
        .addNode("answer", (state, runtime) -> completedFuture(new StateUpdate(Map.of("answer", "done"))))
        .addEdge(StateGraph.START, "research")
        .addEdge("research", "answer")
        .addEdge("answer", StateGraph.END)
        .compile();

RunResult<GraphState> result = graph.invoke("customer-42", GraphState.empty()).toCompletableFuture().join();
```

`Command` combines an update with an explicit route, while `Send` dynamically fans out work. The runtime executes each superstep concurrently up to its configured limit and merges updates in stable node order. `CheckpointStore` persists a snapshot after every superstep; `InMemoryCheckpointStore` is the reference implementation. Production stores receive a `StateCodec` from the application rather than relying on implicit object serialization.

Use `interruptBefore`, `interruptAfter`, or an `Interrupt` result to pause a graph. The returned `ResumeToken` resumes the same thread with a caller-supplied `StateUpdate`; `fork(checkpointId)` starts a new run from history. `stream` exposes state, update, retry, checkpoint, interrupt, debug, and custom node events through `Flow.Publisher`. `CompiledGraph.toMermaid()` exports its topology.

`cortavyn-chat` provides `ChatAgentNode` as the optional bridge: applications create a run-scoped `ChatSession` from `NodeRuntime`, making the graph run ID available when constructing `ToolRuntime`. Use `GraphToolProgressWriter` in that runtime to expose tool progress as graph custom events.

## Structured output

Request a Java record directly from any chat model. Cortavyn derives JSON Schema from nested records, lists, maps and enums, rejects unknown or mistyped fields locally, and keeps the original provider response available for usage and request metadata.

```java
record Weather(String city, int temperature) { }

Weather answer = model.withStructuredOutput(Weather.class)
        .complete(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, "Weather in Berlin?"))))
        .toCompletableFuture().join().value();
```

OpenAI and Azure use strict JSON Schema response formats; Gemini uses `responseMimeType` and `responseJsonSchema`; Anthropic uses a forced schema tool. Mistral and OpenAI-compatible adapters request their JSON Schema response format. Other models receive a synthetic tool automatically, and plain JSON text remains supported as a fallback. Invalid responses throw `StructuredOutputException`, whose `violations()` identifies every invalid field.

## Build

Requires Java 25 and Maven 3.9+.

```shell
mvn verify
```

## Examples

The examples are executable smoke-test applications. They are compiled by the normal build but never call a provider automatically.

```shell
OPENAI_API_KEY=... mvn -pl :cortavyn-example-openai-chat -am package -Prun-example
OPENAI_API_KEY=... mvn -pl :cortavyn-example-openai-tool-agent -am package -Prun-example
MISTRAL_API_KEY=... mvn -pl :cortavyn-example-mistral-chat -am package -Prun-example
MISTRAL_API_KEY=... mvn -pl :cortavyn-example-mistral-tool-agent -am package -Prun-example
MISTRAL_API_KEY=... mvn -pl :cortavyn-example-mistral-structured-output -am package -Prun-example
MISTRAL_API_KEY=... mvn -pl :cortavyn-example-mistral-operations -am package -Prun-example
GEMINI_API_KEY=... mvn -pl :cortavyn-example-gemini-chat -am package -Prun-example
OPENROUTER_API_KEY=... mvn -pl :cortavyn-example-openrouter-chat -am package -Prun-example
ANTHROPIC_API_KEY=... mvn -pl :cortavyn-example-anthropic-chat -am package -Prun-example
mvn -pl :cortavyn-example-ollama-chat -am package -Prun-example
AZURE_OPENAI_ENDPOINT=... AZURE_OPENAI_API_KEY=... AZURE_OPENAI_DEPLOYMENT=... AZURE_OPENAI_API_VERSION=... mvn -pl :cortavyn-example-azure-openai-chat -am package -Prun-example
AWS_BEDROCK_MODEL=... mvn -pl :cortavyn-example-aws-bedrock-chat -am package -Prun-example
```

Pass a prompt as Maven property with `-Dexample.prompt="Explain durable agents in one sentence."`. Mistral examples are grouped under `examples/mistral`; the operations example demonstrates the profile registry, factory, cache, retry/backoff, bounded concurrency, and metrics. The OpenAI example also accepts `OPENAI_MODEL`; Mistral uses the provider default unless `MISTRAL_MODEL` is set. The Gemini example follows LangChain's environment convention: `GOOGLE_API_KEY` takes precedence over `GEMINI_API_KEY`, and `GEMINI_MODEL` overrides its `gemini-2.5-flash` default.
OpenRouter uses `OPENROUTER_MODEL` to choose a catalog model, plus optional `OPENROUTER_SITE_URL` and `OPENROUTER_APP_TITLE` for application attribution.
Azure OpenAI requires its resource endpoint, API key, deployment name, and API version.
Bedrock uses the AWS SDK default credential and region provider chains; set `AWS_BEDROCK_MODEL` to a model ID available in the selected region.
Anthropic accepts `ANTHROPIC_MODEL`.
The local Ollama example defaults to `http://localhost:11434` and `llama3.2`; override them with `OLLAMA_BASE_URL` and `OLLAMA_MODEL`.

## License

Apache-2.0. See [LICENSE](LICENSE).
