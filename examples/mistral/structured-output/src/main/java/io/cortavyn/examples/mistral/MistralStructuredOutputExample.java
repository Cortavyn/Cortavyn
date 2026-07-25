package io.cortavyn.examples.mistral;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.model.api.SchemaDescription;
import io.cortavyn.model.api.SchemaName;
import io.cortavyn.provider.mistral.MistralChatModel;
import java.util.List;

/** Requests a typed record from Mistral using its native JSON Schema response format. */
public final class MistralStructuredOutputExample {
    private MistralStructuredOutputExample() { }

    public static void main(String[] args) {
        String prompt = System.getProperty("example.prompt", "Give the weather in Berlin in Celsius.");
        var builder = MistralChatModel.builder().apiKey(requiredEnvironment("MISTRAL_API_KEY"));
        String modelName = System.getenv("MISTRAL_MODEL");
        if (modelName != null && !modelName.isBlank()) builder.modelName(modelName);

        Weather answer = builder.build().withStructuredOutput(Weather.class)
                .complete(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, prompt))))
                .toCompletableFuture().join().value();
        System.out.printf("%s: %.1f %s (%s)%n", answer.city(), answer.temperature(), answer.unit(), answer.summary());
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be set");
        return value;
    }

    @SchemaName("weather_answer")
    @SchemaDescription("The weather requested by the user.")
    private record Weather(
            @SchemaDescription("City the weather applies to.") String city,
            @SchemaDescription("Temperature as a Celsius number.") double temperature,
            @SchemaDescription("Temperature unit, always Celsius.") String unit,
            @SchemaDescription("Short weather description.") String summary) { }
}
