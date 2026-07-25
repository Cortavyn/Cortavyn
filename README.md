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
| `cortavyn-graph` | Graph definitions and execution contracts |
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
GEMINI_API_KEY=... mvn -pl :cortavyn-example-gemini-chat -am package -Prun-example
OPENROUTER_API_KEY=... mvn -pl :cortavyn-example-openrouter-chat -am package -Prun-example
ANTHROPIC_API_KEY=... mvn -pl :cortavyn-example-anthropic-chat -am package -Prun-example
mvn -pl :cortavyn-example-ollama-chat -am package -Prun-example
AZURE_OPENAI_ENDPOINT=... AZURE_OPENAI_API_KEY=... AZURE_OPENAI_DEPLOYMENT=... AZURE_OPENAI_API_VERSION=... mvn -pl :cortavyn-example-azure-openai-chat -am package -Prun-example
AWS_BEDROCK_MODEL=... mvn -pl :cortavyn-example-aws-bedrock-chat -am package -Prun-example
```

Pass a prompt as Maven property with `-Dexample.prompt="Explain durable agents in one sentence."`. The OpenAI example also accepts `OPENAI_MODEL`; Mistral uses the provider default unless `MISTRAL_MODEL` is set. The Gemini example follows LangChain's environment convention: `GOOGLE_API_KEY` takes precedence over `GEMINI_API_KEY`, and `GEMINI_MODEL` overrides its `gemini-2.5-flash` default.
OpenRouter uses `OPENROUTER_MODEL` to choose a catalog model, plus optional `OPENROUTER_SITE_URL` and `OPENROUTER_APP_TITLE` for application attribution.
Azure OpenAI requires its resource endpoint, API key, deployment name, and API version.
Bedrock uses the AWS SDK default credential and region provider chains; set `AWS_BEDROCK_MODEL` to a model ID available in the selected region.
Anthropic accepts `ANTHROPIC_MODEL`.
The local Ollama example defaults to `http://localhost:11434` and `llama3.2`; override them with `OLLAMA_BASE_URL` and `OLLAMA_MODEL`.

## License

Apache-2.0. See [LICENSE](LICENSE).
