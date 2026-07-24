package io.cortavyn.deep;

import io.cortavyn.model.api.ChatModel;
import java.util.concurrent.CompletionStage;

/** Plans a goal with a model and returns a graph that can be executed separately. */
@FunctionalInterface
public interface DeepAgent {
    CompletionStage<DeepAgentPlan> plan(String goal, ChatModel model);
}
