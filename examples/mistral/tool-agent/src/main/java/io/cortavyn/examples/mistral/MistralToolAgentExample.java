package io.cortavyn.examples.mistral;

import io.cortavyn.chat.ChatAgent;
import io.cortavyn.chat.ChatTool;
import io.cortavyn.chat.Conversation;
import io.cortavyn.chat.ToolDescription;
import io.cortavyn.chat.ToolExecutionResult;
import io.cortavyn.chat.ToolName;
import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.provider.mistral.MistralChatModel;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Runs a Mistral agent that can use an application-owned weather tool. */
public final class MistralToolAgentExample {
    private MistralToolAgentExample() { }

    public static void main(String[] args) {
        var modelBuilder = MistralChatModel.builder().apiKey(requiredEnvironment("MISTRAL_API_KEY"));
        String modelName = System.getenv("MISTRAL_MODEL");
        if (modelName != null && !modelName.isBlank()) modelBuilder.modelName(modelName);

        var weather = ChatTool.typed(WeatherArguments.class, arguments ->
                CompletableFuture.completedFuture(ToolExecutionResult.success(
                        "The weather in " + arguments.city() + " is sunny and 22°C.")));
        var agent = ChatAgent.builder(modelBuilder.build()).tools(weather).build();
        String prompt = System.getProperty("example.prompt", "What is the weather in Berlin?");
        Conversation result = agent.reply(new Conversation("example", List.of()), new ChatMessage(ChatMessageRole.USER, prompt))
                .toCompletableFuture().join();
        System.out.println(result.messages().getLast().content());
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }

    @ToolName("get_weather")
    @ToolDescription("Gets the current weather for a city.")
    private record WeatherArguments(@ToolDescription("The city to look up.") String city) { }
}
