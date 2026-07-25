package io.cortavyn.graph;

import java.util.Objects;

/** Opaque reference to an interrupted checkpoint. */
public record ResumeToken(String checkpointId) { public ResumeToken { Objects.requireNonNull(checkpointId, "checkpointId must not be null"); } }
