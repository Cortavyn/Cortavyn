package io.cortavyn.deep;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Declarative review configuration for model-requested actions. */
public final class ApprovalPolicy {
    private final Map<String, List<ApprovalDecision.Type>> rules;
    private ApprovalPolicy(Map<String, List<ApprovalDecision.Type>> rules) { this.rules = Map.copyOf(rules); }
    public static ApprovalPolicy none() { return new ApprovalPolicy(Map.of()); }
    public static ApprovalPolicy writesAndExecute() { return builder().require("write_file").require("edit_file").require("write_memory").require("execute").build(); }
    public List<ApprovalDecision.Type> decisionsFor(String toolName) { return rules.getOrDefault(toolName, List.of()); }
    public boolean requiresApproval(String toolName) { return !decisionsFor(toolName).isEmpty(); }
    public static Builder builder() { return new Builder(); }
    public static final class Builder {
        private final Map<String, List<ApprovalDecision.Type>> rules = new LinkedHashMap<>();
        public Builder require(String toolName, ApprovalDecision.Type... allowed) { Objects.requireNonNull(toolName, "toolName must not be null"); rules.put(toolName, allowed.length == 0 ? List.of(ApprovalDecision.Type.APPROVE, ApprovalDecision.Type.EDIT, ApprovalDecision.Type.REJECT) : List.of(allowed)); return this; }
        public ApprovalPolicy build() { return new ApprovalPolicy(rules); }
    }
}
