package io.cortavyn.deep;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Routes each relative path to the longest matching mounted workspace subtree. */
public final class CompositeWorkspace implements DeepWorkspace, DeepWorkspaceFiles {
    private final List<WorkspaceMount> mounts;
    public CompositeWorkspace(List<WorkspaceMount> mounts) {
        this.mounts = List.copyOf(Objects.requireNonNull(mounts, "mounts must not be null")).stream().sorted(Comparator.comparingInt((WorkspaceMount mount) -> mount.prefix().length()).reversed()).toList();
        if (this.mounts.isEmpty()) throw new IllegalArgumentException("mounts must not be empty");
    }
    @Override public CompletionStage<List<WorkspaceEntry>> list(String path) {
        if (path == null || path.isBlank() || ".".equals(path)) return CompletableFuture.completedFuture(mounts.stream().map(mount -> new WorkspaceEntry(mount.prefix(), 0, java.time.Instant.EPOCH)).sorted(Comparator.comparing(WorkspaceEntry::path)).toList());
        return route(path).workspace().list(route(path).local()).thenApply(entries -> entries.stream().map(entry -> new WorkspaceEntry(route(path).mount().prefix() + "/" + entry.path(), entry.size(), entry.modifiedAt())).toList());
    }
    @Override public CompletionStage<String> read(String path) { Route route = route(path); return route.workspace().read(route.local()); }
    @Override public CompletionStage<Void> write(String path, String content) { Route route = route(path); return route.workspace().write(route.local(), content); }
    @Override public CompletionStage<Boolean> edit(String path, String expected, String replacement, boolean all) { Route route = route(path); return route.workspace().edit(route.local(), expected, replacement, all); }
    @Override public CompletionStage<List<String>> glob(String pattern) { return merge(mounts.stream().map(mount -> mount.workspace().glob("**").thenApply(paths -> paths.stream().map(path -> mount.prefix() + "/" + path).filter(path -> matches(pattern, path)).toList())).toList()); }
    @Override public CompletionStage<List<WorkspaceMatch>> grep(String query, String pattern) { return merge(mounts.stream().map(mount -> mount.workspace().grep(query, "**").thenApply(matches -> matches.stream().map(match -> new WorkspaceMatch(mount.prefix() + "/" + match.path(), match.line(), match.content())).filter(match -> matches(pattern, match.path())).toList())).toList()); }
    @Override public CompletionStage<WorkspaceRead> readFile(String path, int offset, int limit) { Route route = route(path); if (!(route.workspace() instanceof DeepWorkspaceFiles files)) return CompletableFuture.failedStage(new UnsupportedOperationException("bounded reads are not supported for " + path)); return files.readFile(route.local(), offset, limit); }
    private Route route(String path) { return mounts.stream().filter(mount -> path.equals(mount.prefix()) || path.startsWith(mount.prefix() + "/")).findFirst().map(mount -> new Route(mount, path.equals(mount.prefix()) ? "" : path.substring(mount.prefix().length() + 1))).orElseThrow(() -> new IllegalArgumentException("no workspace mount for path: " + path)); }
    private static boolean matches(String glob, String path) {
        StringBuilder expression = new StringBuilder("^");
        for (int index = 0; index < glob.length(); index++) {
            char character = glob.charAt(index);
            if (character == '*' && index + 1 < glob.length() && glob.charAt(index + 1) == '*') { expression.append(".*"); index++; }
            else if (character == '*') expression.append("[^/]*");
            else expression.append(java.util.regex.Pattern.quote(String.valueOf(character)));
        }
        return path.matches(expression.append('$').toString());
    }
    private static <T> CompletionStage<List<T>> merge(List<CompletionStage<List<T>>> stages) { CompletableFuture<?>[] pending = stages.stream().map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new); return CompletableFuture.allOf(pending).thenApply(ignored -> stages.stream().flatMap(stage -> stage.toCompletableFuture().join().stream()).sorted(Comparator.comparing(Object::toString)).toList()); }
    private record Route(WorkspaceMount mount, String local) { DeepWorkspace workspace() { return mount.workspace(); } }
}
