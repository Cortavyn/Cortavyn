package io.cortavyn.deep;
import java.util.Map;
import org.jspecify.annotations.Nullable;
/** Human response to a pending action. */
public record ApprovalDecision(Type type, @Nullable Map<String, Object> arguments, @Nullable String message) { public enum Type { APPROVE, EDIT, REJECT, RESPOND } }
