package io.cortavyn.deep;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Stores the current structured plan for a deep-agent thread. */
public interface DeepTodoStore {
    CompletionStage<Void> replace(String threadId, List<DeepTodo> todos);
    CompletionStage<List<DeepTodo>> read(String threadId);
    static DeepTodoStore inMemory() { return new InMemoryDeepTodoStore(); }
}
