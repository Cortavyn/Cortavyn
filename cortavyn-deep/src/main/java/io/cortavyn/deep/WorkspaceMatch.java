package io.cortavyn.deep;
/** One line matched by a workspace grep operation. */
public record WorkspaceMatch(String path, int line, String content) { }
