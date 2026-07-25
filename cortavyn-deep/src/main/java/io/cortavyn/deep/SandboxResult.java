package io.cortavyn.deep;
/** Captured result from a sandbox command. */
public record SandboxResult(int exitCode, String stdout, String stderr) { }
