package io.cortavyn.deep;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/** Applies first-match-wins path permissions to another workspace. */
public final class PermissionedWorkspace implements DeepWorkspace {
    private final DeepWorkspace delegate;
    private final List<WorkspacePermission> rules;
    public PermissionedWorkspace(DeepWorkspace delegate, List<WorkspacePermission> rules) { this.delegate = Objects.requireNonNull(delegate, "delegate must not be null"); this.rules = List.copyOf(rules); }
    @Override public CompletionStage<List<WorkspaceEntry>> list(String path) {
        return checked(path, WorkspacePermission.Operation.READ, () -> delegate.list(path)
                .thenApply(entries -> entries.stream().filter(entry -> allowed(entry.path(), WorkspacePermission.Operation.READ)).toList()));
    }
    @Override public CompletionStage<String> read(String path) { return checked(path, WorkspacePermission.Operation.READ, () -> delegate.read(path)); }
    @Override public CompletionStage<Void> write(String path, String content) { return checked(path, WorkspacePermission.Operation.WRITE, () -> delegate.write(path, content)); }
    @Override public CompletionStage<Boolean> edit(String path, String expected, String replacement, boolean all) { return checked(path, WorkspacePermission.Operation.WRITE, () -> delegate.edit(path, expected, replacement, all)); }
    @Override public CompletionStage<List<String>> glob(String pattern) {
        return checked(pattern, WorkspacePermission.Operation.READ, () -> delegate.glob(pattern)
                .thenApply(paths -> paths.stream().filter(path -> allowed(path, WorkspacePermission.Operation.READ)).toList()));
    }
    @Override public CompletionStage<List<WorkspaceMatch>> grep(String query, String pattern) {
        return checked(pattern, WorkspacePermission.Operation.READ, () -> delegate.grep(query, pattern)
                .thenApply(matches -> matches.stream().filter(match -> allowed(match.path(), WorkspacePermission.Operation.READ)).toList()));
    }
    private <T> CompletionStage<T> checked(String path, WorkspacePermission.Operation operation, java.util.function.Supplier<CompletionStage<T>> action) {
        if (!allowed(path, operation)) return CompletableFuture.failedStage(new SecurityException("workspace " + operation.name().toLowerCase(Locale.ROOT) + " denied: " + path));
        return action.get();
    }
    private boolean allowed(String path, WorkspacePermission.Operation operation) {
        // Ordered rules make a narrow deny (for example .env) override a later broad allow.
        for (WorkspacePermission rule : rules) {
            if (rule.operations().contains(operation) && matches(rule.pattern(), path)) return rule.mode() == WorkspacePermission.Mode.ALLOW;
        }
        return true;
    }
    // Keep glob handling local and path-separator-aware; '*' never crosses a directory boundary.
    private static boolean matches(String glob, String path) { StringBuilder regex = new StringBuilder("^"); for (int index = 0; index < glob.length(); index++) { char character = glob.charAt(index); if (character == '*' && index + 1 < glob.length() && glob.charAt(index + 1) == '*') { regex.append(".*"); index++; } else if (character == '*') regex.append("[^/]*"); else regex.append(Pattern.quote(String.valueOf(character))); } return Pattern.compile(regex.append('$').toString()).matcher(path).matches(); }
}
