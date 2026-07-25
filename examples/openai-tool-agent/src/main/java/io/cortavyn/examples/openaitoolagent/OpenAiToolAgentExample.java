package io.cortavyn.examples.openaitoolagent;

import io.cortavyn.chat.ChatAgent;
import io.cortavyn.chat.ChatTool;
import io.cortavyn.chat.Conversation;
import io.cortavyn.chat.ToolExecutionResult;
import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ToolDefinition;
import io.cortavyn.provider.openai.OpenAiChatModel;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Runs an agent that can use an application-owned weather tool. */
public final class OpenAiToolAgentExample {
    private OpenAiToolAgentExample() { }

    public static void main(String[] args) {
        String apiKey = requiredEnvironment("OPENAI_API_KEY");
        var model = OpenAiChatModel.builder().apiKey(apiKey).modelName(System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4.1-mini")).build();
        var weather = new ChatTool(new ToolDefinition("get_weather", "Gets the current weather for a city.", Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string")), "required", java.util.List.of("city"))), call -> {
            String city = String.valueOf(call.arguments().get("city"));
            return CompletableFuture.completedFuture(ToolExecutionResult.success("The weather in " + city + " is sunny and 22°C."));
        });
        var agent = ChatAgent.builder(model).tools(weather).build();
        String prompt = System.getProperty("example.prompt", "What is the weather in Berlin?");
        Conversation result = agent.reply(new Conversation("example", java.util.List.of()), new ChatMessage(ChatMessageRole.USER, prompt)).toCompletableFuture().join();
        System.out.println(result.messages().getLast().content());
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }
}
