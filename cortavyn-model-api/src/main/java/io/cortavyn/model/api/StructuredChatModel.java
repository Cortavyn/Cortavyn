package io.cortavyn.model.api;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
/** Typed facade over a chat model that requests and parses one record-shaped response. */
public final class StructuredChatModel<T> {
 private static final ObjectMapper JSON=new ObjectMapper(); private final ChatModel model; private final Class<T> type; private final StructuredOutputSchema schema;
 StructuredChatModel(ChatModel model,Class<T> type){this.model=Objects.requireNonNull(model);this.type=Objects.requireNonNull(type);this.schema=StructuredSchemas.fromRecord(type);}
 /** Requests and parses a value, preferring native schema support and otherwise using a synthetic tool. */
 public CompletionStage<StructuredOutputResult<T>> complete(ChatRequest request){ CompletionStage<ChatResponse> response=model instanceof StructuredOutputChatModel nativeModel?nativeModel.complete(request,schema):model.complete(withSyntheticTool(request)); return response.thenApply(value->parse(value)); }
 public StructuredOutputSchema schema(){return schema;}
 private ChatRequest withSyntheticTool(ChatRequest request){List<ToolDefinition> tools=new ArrayList<>(request.tools());if(tools.stream().anyMatch(tool->tool.name().equals(schema.name())))throw new IllegalArgumentException("structured output schema name conflicts with a tool: "+schema.name());tools.add(new ToolDefinition(schema.name(),"Return the requested structured response using this schema.",schema.jsonSchema()));return new ChatRequest(request.messages(),tools,request.parameters(),request.extensions());}
 private StructuredOutputResult<T> parse(ChatResponse response){try{for(ToolCall call:response.message().toolCalls())if(call.name().equals(schema.name()))return new StructuredOutputResult<>(JSON.convertValue(call.arguments(),type),response);return new StructuredOutputResult<>(JSON.readValue(response.message().content(),type),response);}catch(Exception e){throw new StructuredOutputException("Response is not valid "+type.getSimpleName()+" JSON or a matching structured-output tool call",e);}}
}
