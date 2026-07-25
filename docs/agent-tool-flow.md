# Agent and tool flow

`ChatAgent` owns the model/tool loop. Provider adapters only translate portable messages to and from a provider protocol; they never execute application tools.

```mermaid
sequenceDiagram
    participant App as Application
    participant Agent as ChatAgent
    participant Model as ChatModel
    participant Tool as ChatTool
    App->>Agent: reply(conversation, user message)
    Agent->>Model: messages + tool definitions
    Model-->>Agent: assistant message + tool calls
    loop each tool round (up to maxIterations)
        par every requested tool
            Agent->>Tool: execute(call, ToolRuntime)
            Tool-->>Agent: ToolExecutionResult
        end
        Agent->>Model: assistant call + tool-result messages
        Model-->>Agent: final answer or more tool calls
    end
    Agent-->>App: updated Conversation
```

## Runtime ownership

```mermaid
flowchart LR
    A[Application] -->|configures once| R[ToolRuntime]
    R --> C[User context]
    R --> S[ToolStore]
    R --> P[ToolProgressWriter]
    R -->|shared by an agent run| T[Runtime-aware tool]
    T --> U[ToolExecutionResult]
    U --> M[Portable ChatMessage]
```

`ToolStore` is an application boundary: `InMemoryToolStore` is suitable for examples and tests, while production callers provide a durable implementation. `ToolExecutionResult` can contain text, image, audio, or document blocks. Provider-specific wire mappings remain separate adapter work.
