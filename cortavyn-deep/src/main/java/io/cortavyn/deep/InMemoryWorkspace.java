package io.cortavyn.deep;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Thread-safe, process-local workspace suited to tests and isolated agent runs. */
public final class InMemoryWorkspace implements CheckpointableWorkspace {
    private final Map<String, File> files = new ConcurrentHashMap<>();
    @Override public CompletionStage<List<WorkspaceEntry>> list(String path) { String prefix = normalizeDirectory(path); return CompletableFuture.completedFuture(files.entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix)).map(entry -> new WorkspaceEntry(entry.getKey(), entry.getValue().content().length(), entry.getValue().modifiedAt())).sorted(Comparator.comparing(WorkspaceEntry::path)).toList()); }
    @Override public CompletionStage<String> read(String path) { File file = files.get(normalize(path)); return file == null ? CompletableFuture.failedStage(new IllegalArgumentException("file not found: " + path)) : CompletableFuture.completedFuture(file.content()); }
    @Override public CompletionStage<Void> write(String path, String content) { files.put(normalize(path), new File(content, Instant.now())); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Boolean> edit(String path, String expected, String replacement, boolean all) { String key = normalize(path); File file = files.get(key); if (file == null || !file.content().contains(expected)) return CompletableFuture.completedFuture(false); String next = all ? file.content().replace(expected, replacement) : file.content().replaceFirst(Pattern.quote(expected), java.util.regex.Matcher.quoteReplacement(replacement)); files.put(key, new File(next, Instant.now())); return CompletableFuture.completedFuture(true); }
    @Override public CompletionStage<List<String>> glob(String pattern) { Pattern regex = Pattern.compile(globRegex(pattern)); return CompletableFuture.completedFuture(files.keySet().stream().filter(path -> regex.matcher(path).matches()).sorted().toList()); }
    @Override public CompletionStage<List<WorkspaceMatch>> grep(String query, String pattern) { Pattern regex = Pattern.compile(globRegex(pattern)); List<WorkspaceMatch> result = new ArrayList<>(); files.forEach((path, file) -> { if (regex.matcher(path).matches()) { String[] lines = file.content().split("\\R", -1); for (int i = 0; i < lines.length; i++) if (lines[i].contains(query)) result.add(new WorkspaceMatch(path, i + 1, lines[i])); } }); result.sort(Comparator.comparing(WorkspaceMatch::path).thenComparingInt(WorkspaceMatch::line)); return CompletableFuture.completedFuture(List.copyOf(result)); }
    // Snapshots contain content rather than implementation details such as timestamps, so they
    // can be persisted by any DeepRunStore and restored into a fresh agent instance.
    @Override public CompletionStage<WorkspaceSnapshot> snapshot() { return CompletableFuture.completedFuture(new WorkspaceSnapshot(files.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().content())))); }
    @Override public CompletionStage<Void> restore(WorkspaceSnapshot snapshot) { files.clear(); snapshot.files().forEach((path, content) -> files.put(normalize(path), new File(content, Instant.now()))); return CompletableFuture.completedFuture(null); }
    // Reject absolute paths and traversal segments before they reach any workspace operation.
    private static String normalize(String path) { if (path == null || path.isBlank() || path.startsWith("/") || path.contains("..")) throw new IllegalArgumentException("workspace path must be a safe relative path"); return path.replaceAll("/+", "/"); }
    private static String normalizeDirectory(String path) { return path == null || path.isBlank() || ".".equals(path) ? "" : normalize(path).replaceAll("/?$", "/"); }
    private static String globRegex(String glob) {
        String safe = normalize(glob); StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < safe.length(); index++) {
            char current = safe.charAt(index);
            if (current == '*' && index + 1 < safe.length() && safe.charAt(index + 1) == '*') { regex.append(".*"); index++; }
            else if (current == '*') regex.append("[^/]*");
            else regex.append(Pattern.quote(String.valueOf(current)));
        }
        return regex.append('$').toString();
    }
    private record File(String content, Instant modifiedAt) { }
}
