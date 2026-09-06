package io.cortavyn.deep;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Read-only MCP resource projection; URI strings are the stable workspace paths. */
final class McpResourceWorkspace implements DeepWorkspace {
    private final List<WorkspaceEntry> entries; private final java.util.function.Function<String, CompletionStage<List<io.cortavyn.model.api.ChatContent>>> reader;
    McpResourceWorkspace(JsonNode resources, java.util.function.Function<String, CompletionStage<List<io.cortavyn.model.api.ChatContent>>> reader) { this.entries = java.util.stream.StreamSupport.stream(resources.spliterator(), false).map(resource -> new WorkspaceEntry(resource.path("uri").asText(), 0, Instant.EPOCH)).toList(); this.reader = reader; }
    @Override public CompletionStage<List<WorkspaceEntry>> list(String path) { return CompletableFuture.completedFuture(entries); }
    @Override public CompletionStage<String> read(String path) { return reader.apply(path).thenApply(blocks -> blocks.stream().filter(io.cortavyn.model.api.TextContent.class::isInstance).map(io.cortavyn.model.api.TextContent.class::cast).map(io.cortavyn.model.api.TextContent::text).collect(java.util.stream.Collectors.joining())); }
    @Override public CompletionStage<Void> write(String path, String content) { return CompletableFuture.failedStage(new UnsupportedOperationException("MCP resources are read-only")); }
    @Override public CompletionStage<Boolean> edit(String path, String expected, String replacement, boolean all) { return CompletableFuture.failedStage(new UnsupportedOperationException("MCP resources are read-only")); }
    @Override public CompletionStage<List<String>> glob(String pattern) { return CompletableFuture.completedFuture(entries.stream().map(WorkspaceEntry::path).toList()); }
    @Override public CompletionStage<List<WorkspaceMatch>> grep(String query, String pattern) { return CompletableFuture.completedFuture(List.of()); }
}
