package io.cortavyn.deep;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Pluggable durable key/value store for workspace files. */
public interface WorkspaceStore {
    CompletionStage<Optional<File>> get(String path);
    CompletionStage<Void> put(String path, String content);
    CompletionStage<List<Entry>> list();
    record File(String content, Instant modifiedAt) { }
    record Entry(String path, long size, Instant modifiedAt) { }
}
