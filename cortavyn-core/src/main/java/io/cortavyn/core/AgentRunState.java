package io.cortavyn.core;

/** Lifecycle states shared by executable Cortavyn workloads. */
public enum AgentRunState {
    PENDING, RUNNING, SUCCEEDED, FAILED, CANCELLED
}
