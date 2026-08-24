package io.cortavyn.deep;

import java.util.EnumSet;
import java.util.Set;

/** Model/deployment-specific selection of built-in Deep Agent capabilities. */
public record DeepHarnessProfile(Set<BuiltIn> enabled) {
    public DeepHarnessProfile { enabled = Set.copyOf(enabled); }
    public boolean enables(BuiltIn builtIn) { return enabled.contains(builtIn); }
    public static DeepHarnessProfile defaults() { return new DeepHarnessProfile(EnumSet.allOf(BuiltIn.class)); }
    public enum BuiltIn { WORKSPACE, TODOS, SKILLS, MEMORY, SANDBOX, INTERPRETER, SUBAGENTS, MCP_RESOURCES }
}
