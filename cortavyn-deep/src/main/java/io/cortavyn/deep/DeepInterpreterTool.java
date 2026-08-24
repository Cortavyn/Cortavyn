package io.cortavyn.deep;

import java.util.concurrent.CompletionStage;

/** Explicit, named capability callable by a QuickJS program through {@code tools.<name>(arguments)}. */
public interface DeepInterpreterTool {
    String name();
    CompletionStage<String> call(String argumentsJson);
}
