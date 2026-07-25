package io.cortavyn.deep;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;

/** Explicit code-execution boundary. Implementations must isolate or otherwise constrain commands. */
public interface Sandbox {
    CompletionStage<SandboxResult> execute(List<String> command, Duration timeout);
}
