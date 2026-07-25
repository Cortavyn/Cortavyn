package io.cortavyn.deep;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Permission-aware virtual filesystem used by a deep-agent run. Paths are always relative. */
public interface DeepWorkspace {
    CompletionStage<List<WorkspaceEntry>> list(String path);
    CompletionStage<String> read(String path);
    CompletionStage<Void> write(String path, String content);
    CompletionStage<Boolean> edit(String path, String expected, String replacement, boolean all);
    CompletionStage<List<String>> glob(String pattern);
    CompletionStage<List<WorkspaceMatch>> grep(String query, String pattern);
}
