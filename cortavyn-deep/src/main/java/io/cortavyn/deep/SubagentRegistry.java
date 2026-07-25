package io.cortavyn.deep;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/** Run-scoped registry for asynchronous delegated work. */
final class SubagentRegistry {
    private final Map<String, CompletionStage<String>> tasks = new ConcurrentHashMap<>();
    private final DeepTaskStore store;
    private final BiFunction<String, String, CompletionStage<String>> dispatcher;
    SubagentRegistry(DeepTaskStore store, BiFunction<String, String, CompletionStage<String>> dispatcher) { this.store = store; this.dispatcher = dispatcher; }
    // Persist RUNNING before dispatching. If this process disappears, await() can discover the
    // saved task and ask the dispatcher to restart it in a later agent instance.
    String start(String agent, String prompt) { String id = UUID.randomUUID().toString(); store.save(new DeepSubagentTask(id, agent, prompt, DeepSubagentTask.Status.RUNNING, null, null)); launch(id, agent, prompt); return id; }
    CompletionStage<String> run(String agent, String prompt) { return dispatcher.apply(agent, prompt); }
    CompletionStage<String> await(String id) { CompletionStage<String> task = tasks.get(id); if (task != null) return task; return store.get(id).thenCompose(found -> { DeepSubagentTask saved = found.orElseThrow(() -> new IllegalArgumentException("unknown subagent task: " + id)); // A missing local future means this is a recovered process, not necessarily a failed task.
        return switch (saved.status()) { case COMPLETED -> CompletableFuture.completedFuture(saved.result()); case FAILED -> CompletableFuture.failedStage(new IllegalStateException(saved.failure())); case RUNNING -> launch(id, saved.agent(), saved.prompt()); }; }); }
    private CompletionStage<String> launch(String id, String agent, String prompt) {
        CompletionStage<String> task = dispatcher.apply(agent, prompt);
        CompletionStage<String> installed = tasks.putIfAbsent(id, task);
        if (installed != null) return installed;
        task.whenComplete((result, failure) -> {
            tasks.remove(id, task);
            store.save(failure == null ? new DeepSubagentTask(id, agent, prompt, DeepSubagentTask.Status.COMPLETED, result, null) : new DeepSubagentTask(id, agent, prompt, DeepSubagentTask.Status.FAILED, null, String.valueOf(failure.getMessage())));
        });
        return task;
    }
}
