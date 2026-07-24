package io.cortavyn.model.api;
import java.util.concurrent.CompletionStage;
/** A model that can request provider-enforced JSON Schema output. */
public interface StructuredOutputChatModel extends ChatModel { CompletionStage<ChatResponse> complete(ChatRequest request, StructuredOutputSchema schema); }
