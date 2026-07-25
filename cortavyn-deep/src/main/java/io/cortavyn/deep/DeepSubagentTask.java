package io.cortavyn.deep;

import org.jspecify.annotations.Nullable;

/** Persistable lifecycle record for delegated asynchronous work. */
public record DeepSubagentTask(String id, String agent, String prompt, Status status, @Nullable String result, @Nullable String failure) {
    public enum Status { RUNNING, COMPLETED, FAILED }
    public DeepSubagentTask { if (id == null || id.isBlank() || agent == null || agent.isBlank()) throw new IllegalArgumentException("task id and agent must not be blank"); }
}
