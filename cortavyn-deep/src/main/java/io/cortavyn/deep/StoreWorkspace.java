package io.cortavyn.deep;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/** Deep workspace backed by an application-provided, potentially remote {@link WorkspaceStore}. */
public final class StoreWorkspace implements DeepWorkspace, DeepWorkspaceFiles {
    private final WorkspaceStore store;
    public StoreWorkspace(WorkspaceStore store) { this.store = Objects.requireNonNull(store, "store must not be null"); }
    @Override public CompletionStage<List<WorkspaceEntry>> list(String path) { String prefix = directory(path); return store.list().thenApply(entries -> entries.stream().filter(entry -> entry.path().startsWith(prefix)).map(entry -> new WorkspaceEntry(entry.path(), entry.size(), entry.modifiedAt())).toList()); }
    @Override public CompletionStage<String> read(String path) { return store.get(safe(path)).thenCompose(file -> file.<CompletionStage<String>>map(value -> CompletableFuture.completedFuture(value.content())).orElseGet(() -> CompletableFuture.failedStage(new IllegalArgumentException("file not found: " + path)))); }
    @Override public CompletionStage<Void> write(String path, String content) { return store.put(safe(path), Objects.requireNonNull(content, "content must not be null")); }
    @Override public CompletionStage<Boolean> edit(String path, String expected, String replacement, boolean all) { return store.get(safe(path)).thenCompose(file -> { if (file.isEmpty() || !file.get().content().contains(expected)) return CompletableFuture.completedFuture(false); String source = file.get().content(); return store.put(safe(path), all ? source.replace(expected, replacement) : source.replaceFirst(Pattern.quote(expected), java.util.regex.Matcher.quoteReplacement(replacement))).thenApply(ignored -> true); }); }
    @Override public CompletionStage<List<String>> glob(String pattern) { Pattern regex = Pattern.compile(globRegex(pattern)); return store.list().thenApply(entries -> entries.stream().map(WorkspaceStore.Entry::path).filter(path -> regex.matcher(path).matches()).toList()); }
    @Override public CompletionStage<List<WorkspaceMatch>> grep(String query, String pattern) { return glob(pattern).thenCompose(paths -> {
        List<CompletableFuture<List<WorkspaceMatch>>> reads = paths.stream().map(path -> read(path).thenApply(source -> matches(path, source, query)).toCompletableFuture()).toList();
        return CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new)).thenApply(ignored -> reads.stream().flatMap(read -> read.join().stream()).toList());
    }); }
    @Override public CompletionStage<WorkspaceRead> readFile(String path, int offset, int limit) { if (offset < 0 || limit <= 0) return CompletableFuture.failedStage(new IllegalArgumentException("offset must be non-negative and limit must be positive")); return read(path).thenApply(source -> { String[] lines = source.split("\\R", -1); int end = (int) Math.min(lines.length, (long) offset + limit); String text = java.util.stream.IntStream.range(offset, end).mapToObj(index -> (index + 1) + ": " + lines[index]).collect(java.util.stream.Collectors.joining("\n")); return new WorkspaceRead(List.of(new io.cortavyn.model.api.TextContent(text)), offset, lines.length, end < lines.length, source.length()); }); }
    private static List<WorkspaceMatch> matches(String path, String source, String query) { String[] lines = source.split("\\R", -1); return java.util.stream.IntStream.range(0, lines.length).filter(index -> lines[index].contains(query)).mapToObj(index -> new WorkspaceMatch(path, index + 1, lines[index])).toList(); }
    private static String directory(String path) { return path == null || path.isBlank() || ".".equals(path) ? "" : safe(path).replaceAll("[^/]+$", ""); }
    private static String safe(String path) { if (path == null || path.isBlank() || path.startsWith("/") || path.contains("..")) throw new IllegalArgumentException("path must be a safe relative path"); return path; }
    private static String globRegex(String pattern) { StringBuilder expression = new StringBuilder("^"); for (int index = 0; index < pattern.length(); index++) { char character = pattern.charAt(index); if (character == '*' && index + 1 < pattern.length() && pattern.charAt(index + 1) == '*') { expression.append(".*"); index++; } else if (character == '*') expression.append("[^/]*"); else expression.append(Pattern.quote(String.valueOf(character))); } return expression.append('$').toString(); }
}
