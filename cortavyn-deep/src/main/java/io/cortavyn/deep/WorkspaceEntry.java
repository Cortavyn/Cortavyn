package io.cortavyn.deep;
import java.time.Instant;
/** File metadata returned from a virtual workspace. */
public record WorkspaceEntry(String path, long size, Instant modifiedAt) { }
