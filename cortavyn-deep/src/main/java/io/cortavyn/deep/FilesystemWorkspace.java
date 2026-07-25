package io.cortavyn.deep;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/** Marker for a host-backed workspace root. Host implementations must reject paths escaping this root. */
public final class FilesystemWorkspace implements DeepWorkspace {
    private final Path root;
    public FilesystemWorkspace(Path root) { this.root = Objects.requireNonNull(root, "root must not be null").toAbsolutePath().normalize(); }
    public Path root() { return root; }
    // Normalize before checking the prefix so sequences such as "a/../../secret" cannot escape.
    public Path resolve(String relativePath) { Path path = root.resolve(relativePath).normalize(); if (!path.startsWith(root)) throw new IllegalArgumentException("workspace path escapes root"); return path; }
    @Override public CompletionStage<List<WorkspaceEntry>> list(String path) { return supply(() -> { Path directory = resolve(path == null || path.isBlank() || ".".equals(path) ? "" : path); try (var entries = Files.list(directory)) { return entries.map(item -> { try { return new WorkspaceEntry(root.relativize(item).toString(), Files.isDirectory(item) ? 0 : Files.size(item), Files.getLastModifiedTime(item).toInstant()); } catch (IOException failure) { throw new IllegalStateException(failure); } }).sorted(java.util.Comparator.comparing(WorkspaceEntry::path)).toList(); } }); }
    @Override public CompletionStage<String> read(String path) { return supply(() -> Files.readString(resolve(path))); }
    @Override public CompletionStage<Void> write(String path, String content) { return run(() -> { Path target = resolve(path); Files.createDirectories(Objects.requireNonNull(target.getParent(), "workspace root cannot be a file")); Files.writeString(target, content); }); }
    @Override public CompletionStage<Boolean> edit(String path, String expected, String replacement, boolean all) { return supply(() -> { Path target = resolve(path); String source = Files.readString(target); if (!source.contains(expected)) return false; Files.writeString(target, all ? source.replace(expected, replacement) : source.replaceFirst(Pattern.quote(expected), java.util.regex.Matcher.quoteReplacement(replacement))); return true; }); }
    @Override public CompletionStage<List<String>> glob(String pattern) { return supply(() -> { PathMatcher matcher = new PathMatcher(pattern); try (var entries = Files.walk(root)) { return entries.filter(Files::isRegularFile).map(root::relativize).map(path -> path.toString().replace(path.getFileSystem().getSeparator(), "/")).filter(matcher::matches).sorted().toList(); } }); }
    @Override public CompletionStage<List<WorkspaceMatch>> grep(String query, String pattern) { return glob(pattern).thenCompose(paths -> supply(() -> paths.stream().flatMap(path -> { try { String[] lines = Files.readString(resolve(path)).split("\\R", -1); return java.util.stream.IntStream.range(0, lines.length).filter(index -> lines[index].contains(query)).mapToObj(index -> new WorkspaceMatch(path, index + 1, lines[index])); } catch (IOException failure) { throw new IllegalStateException(failure); } }).toList())); }
    private <T> CompletionStage<T> supply(IoSupplier<T> supplier) { try { return CompletableFuture.completedFuture(supplier.get()); } catch (IOException failure) { return CompletableFuture.failedStage(failure); } }
    private CompletionStage<Void> run(IoRunnable runnable) { try { runnable.run(); return CompletableFuture.completedFuture(null); } catch (IOException failure) { return CompletableFuture.failedStage(failure); } }
    @FunctionalInterface private interface IoSupplier<T> { T get() throws IOException; }
    @FunctionalInterface private interface IoRunnable { void run() throws IOException; }
    private static final class PathMatcher {
        private final Pattern regex;
        PathMatcher(String glob) {
            // Glob patterns are input from the model too, so apply the same relative-path rule.
            if (glob == null || glob.isBlank() || glob.startsWith("/") || glob.contains("..")) throw new IllegalArgumentException("glob must be a safe relative pattern");
            StringBuilder expression = new StringBuilder("^");
            for (int index = 0; index < glob.length(); index++) {
                char current = glob.charAt(index);
                if (current == '*' && index + 1 < glob.length() && glob.charAt(index + 1) == '*') { expression.append(".*"); index++; }
                else if (current == '*') expression.append("[^/]*");
                else expression.append(Pattern.quote(String.valueOf(current)));
            }
            regex = Pattern.compile(expression.append('$').toString());
        }
        boolean matches(String path) { return regex.matcher(path).matches(); }
    }
}
