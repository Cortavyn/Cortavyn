package io.cortavyn.deep;

import java.util.concurrent.CompletionStage;

/** Explicit file transfer boundary for isolated execution environments. */
public interface SandboxFiles {
    CompletionStage<Void> putFile(String path, byte[] content);
    CompletionStage<byte[]> getFile(String path);
}
