package io.cortavyn.chat;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/** Application-provided durable key-value namespaces available to tools in an agent run. */
public interface ToolStore {
    CompletionStage<Map<String, Object>> read(String namespace);

    CompletionStage<Void> write(String namespace, Map<String, Object> values);
}
