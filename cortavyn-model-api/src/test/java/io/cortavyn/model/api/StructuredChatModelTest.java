package io.cortavyn.model.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class StructuredChatModelTest {
    @Test
    void parsesSyntheticToolArgumentsIntoRecord() {
        ChatModel model = request -> {
            assertEquals("weather_answer", request.tools().getFirst().name());
            ChatMessage response = new ChatMessage(ChatMessageRole.ASSISTANT, "", List.of(), null,
                    List.of(new ToolCall("call", "weather_answer", Map.of("city", "Berlin", "temperature", 22))), Map.of());
            return CompletableFuture.completedFuture(new ChatResponse(response));
        };

        Weather value = model.withStructuredOutput(Weather.class)
                .complete(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, "Weather?"))))
                .toCompletableFuture().join().value();

        assertEquals("Berlin", value.city());
        assertEquals(22, value.temperature());
    }

    @Test
    void reportsAllSchemaViolationsBeforeRecordConversion() {
        ChatModel model = request -> CompletableFuture.completedFuture(new ChatResponse(new ChatMessage(
                ChatMessageRole.ASSISTANT, "{\"city\":5,\"extra\":true}", List.of(), null, List.of(), Map.of())));

        CompletionException completion = assertThrows(CompletionException.class, () -> model.withStructuredOutput(Weather.class)
                .complete(new ChatRequest(List.of(new ChatMessage(ChatMessageRole.USER, "Weather?")))).toCompletableFuture().join());
        StructuredOutputException exception = (StructuredOutputException) completion.getCause();
        assertEquals(3, exception.violations().size());
    }

    @SchemaName("weather_answer")
    @SchemaDescription("Current weather.")
    private record Weather(@SchemaDescription("Requested city.") String city, int temperature, @Nullable String unit) { }
}
