package io.cortavyn.deep;

import io.cortavyn.chat.ChatTool;
import io.cortavyn.chat.ToolExecutionResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Built-in, provider-neutral tools exposed by a {@link DeepAgent}. */
final class DeepTools {
    private DeepTools() { }
    static List<ChatTool> workspace(DeepWorkspace workspace) {
        return List.of(
                ChatTool.typed("ls", "List files below a workspace directory.", Ls.class, args -> workspace.list(args.path()).thenApply(value -> ToolExecutionResult.success(value.toString()))),
                ChatTool.typed("read_file", "Read a text file from the workspace.", Read.class, args -> workspace.read(args.path()).thenApply(ToolExecutionResult::success)),
                ChatTool.typed("write_file", "Write a text file in the workspace.", Write.class, args -> workspace.write(args.path(), args.content()).thenApply(ignored -> ToolExecutionResult.success("Wrote " + args.path()))),
                ChatTool.typed("edit_file", "Replace exact text in a workspace file.", Edit.class, args -> workspace.edit(args.path(), args.expected(), args.replacement(), args.all()).thenApply(changed -> ToolExecutionResult.success(changed ? "Edit applied" : "Expected text not found"))),
                ChatTool.typed("glob", "Find workspace files using a glob pattern.", Glob.class, args -> workspace.glob(args.pattern()).thenApply(value -> ToolExecutionResult.success(value.toString()))),
                ChatTool.typed("grep", "Find text in workspace files.", Grep.class, args -> workspace.grep(args.query(), args.pattern()).thenApply(value -> ToolExecutionResult.success(value.toString()))));
    }
    static ChatTool todos(DeepTodoStore store) { return ChatTool.typed("write_todos", "Record a structured plan with pending, in-progress, or completed work.", Todos.class, (args, runtime) -> store.replace(runtime.runId(), args.todos()).thenApply(ignored -> ToolExecutionResult.success("Todos recorded: " + args.todos().size()))); }
    static List<ChatTool> skills(List<DeepSkill> skills) {
        if (skills.isEmpty()) return List.of();
        // Index once per turn. The prompt only advertises names/descriptions; instructions and
        // potentially large resource files become visible only through these explicit tools.
        Map<String, DeepSkill> indexed = skills.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(DeepSkill::name, skill -> skill));
        return List.of(
                ChatTool.typed("load_skill", "Load the complete instructions for an available skill.", LoadSkill.class, args -> { DeepSkill skill = indexed.get(args.name()); return CompletableFuture.completedFuture(skill == null ? ToolExecutionResult.failure("Unknown skill: " + args.name()) : ToolExecutionResult.success(skill.instructions())); }),
                ChatTool.typed("read_skill_resource", "Read a resource bundled with an available skill.", ReadSkillResource.class, args -> { DeepSkill skill = indexed.get(args.name()); if (skill == null) return CompletableFuture.completedFuture(ToolExecutionResult.failure("Unknown skill: " + args.name())); String resource = skill.resources().get(args.path()); return CompletableFuture.completedFuture(resource == null ? ToolExecutionResult.failure("Unknown skill resource: " + args.path()) : ToolExecutionResult.success(resource)); }));
    }
    static List<ChatTool> memory(DeepMemory memory, String namespace) {
        return List.of(
                ChatTool.typed("read_memory", "Read persistent caller-scoped agent memory.", ReadMemory.class, ignored -> memory.load(namespace).thenApply(ToolExecutionResult::success)),
                ChatTool.typed("write_memory", "Replace persistent caller-scoped agent memory with reviewed instructions or preferences.", WriteMemory.class, args -> memory.save(namespace, args.content()).thenApply(ignored -> ToolExecutionResult.success("Memory updated."))));
    }
    static List<ChatTool> sandbox(Sandbox sandbox) { return List.of(ChatTool.typed("execute", "Execute an argument-vector command in the configured sandbox.", Execute.class, args -> sandbox.execute(args.command(), java.time.Duration.ofSeconds(args.timeoutSeconds())).thenApply(result -> ToolExecutionResult.success("exit=" + result.exitCode() + "\nstdout:\n" + result.stdout() + "\nstderr:\n" + result.stderr())))); }
    static List<ChatTool> mcpResources(List<McpToolSource> sources) {
        if (sources.isEmpty()) return List.of();
        Map<String, McpToolSource> indexed = sources.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(McpToolSource::name, source -> source));
        return List.of(ChatTool.typed("read_mcp_resource", "Read a resource exposed by a configured MCP source.", ReadMcpResource.class, args -> { McpToolSource source = indexed.get(args.source()); if (source == null) return CompletableFuture.completedFuture(ToolExecutionResult.failure("Unknown MCP source: " + args.source())); return source.resources().read(args.path()).thenApply(ToolExecutionResult::success); }));
    }
    static List<ChatTool> subagents(boolean configured, SubagentRegistry registry) {
        if (!configured) return List.of();
        // task is synchronous; start_task/await_task expose the same specialist work as a
        // durable two-step protocol for long-running delegations.
        return List.of(
                ChatTool.typed("task", "Delegate an isolated multi-step task to a named specialist and wait for its final report.", Task.class, args -> registry.run(args.agent(), args.prompt()).thenApply(ToolExecutionResult::success)),
                ChatTool.typed("start_task", "Start a named specialist asynchronously and return its task id.", Task.class, args -> CompletableFuture.completedFuture(ToolExecutionResult.success(registry.start(args.agent(), args.prompt())))),
                ChatTool.typed("await_task", "Wait for the final report of an asynchronous specialist task.", AwaitTask.class, args -> registry.await(args.taskId()).thenApply(ToolExecutionResult::success)));
    }
    record Ls(String path) { }
    record Read(String path) { }
    record Write(String path, String content) { }
    record Edit(String path, String expected, String replacement, boolean all) { }
    record Glob(String pattern) { }
    record Grep(String query, String pattern) { }
    record Todos(List<DeepTodo> todos) { }
    record Task(String agent, String prompt) { }
    record AwaitTask(String taskId) { }
    record LoadSkill(String name) { }
    record ReadSkillResource(String name, String path) { }
    record ReadMemory() { }
    record WriteMemory(String content) { }
    record Execute(List<String> command, long timeoutSeconds) { public Execute { if (timeoutSeconds <= 0) throw new IllegalArgumentException("timeoutSeconds must be positive"); } }
    record ReadMcpResource(String source, String path) { }
}
