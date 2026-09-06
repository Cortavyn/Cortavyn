package io.cortavyn.deep;

import java.util.concurrent.CompletionStage;

/** Optional rich-file capability for bounded and multimodal workspace reads. */
public interface DeepWorkspaceFiles {
    /** Reads at most {@code limit} zero-based text lines beginning at {@code offset}. */
    CompletionStage<WorkspaceRead> readFile(String path, int offset, int limit);
}
