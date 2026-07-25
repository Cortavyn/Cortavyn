package io.cortavyn.model.api;

import java.util.List;

/** Indicates that an assistant response does not conform to the requested structured schema. */
public final class StructuredOutputException extends RuntimeException {
    private final List<String> violations;

    public StructuredOutputException(String message, Throwable cause) { this(message, List.of(), cause); }
    public StructuredOutputException(String message, List<String> violations) {
        super(message + (violations.isEmpty() ? "" : ": " + String.join("; ", violations)));
        this.violations = List.copyOf(violations);
    }
    public StructuredOutputException(String message, List<String> violations, Throwable cause) {
        super(message + (violations.isEmpty() ? "" : ": " + String.join("; ", violations)), cause);
        this.violations = List.copyOf(violations);
    }
    /** JSON-pointer-like paths explaining every schema violation found in the response. */
    public List<String> violations() { return violations; }
}
