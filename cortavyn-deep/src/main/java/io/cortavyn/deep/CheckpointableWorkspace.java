package io.cortavyn.deep;

import java.util.concurrent.CompletionStage;

/** A workspace whose virtual files can travel with a durable deep-agent checkpoint. */
public interface CheckpointableWorkspace extends DeepWorkspace {
    CompletionStage<WorkspaceSnapshot> snapshot();
    CompletionStage<Void> restore(WorkspaceSnapshot snapshot);
}
