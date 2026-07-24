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

## Build

Requires Java 25 and Maven 3.9+.

```shell
mvn verify
```

## Examples

The examples are executable smoke-test applications. They are compiled by the normal build but never call a provider automatically.

```shell
OPENAI_API_KEY=... mvn -pl :cortavyn-example-openai-chat -am package -Prun-example
MISTRAL_API_KEY=... mvn -pl :cortavyn-example-mistral-chat -am package -Prun-example
```

Pass a prompt as Maven property with `-Dexample.prompt="Explain durable agents in one sentence."`. The OpenAI example also accepts `OPENAI_MODEL`; Mistral uses the provider default unless `MISTRAL_MODEL` is set.

## License

Apache-2.0. See [LICENSE](LICENSE).
