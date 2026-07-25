package io.cortavyn.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.cortavyn.model.api.ToolCall;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ChatToolTest {
    @Test
    void createsSchemaAndTypedExecutorFromAnnotatedRecord() {
        ChatTool tool = ChatTool.typed(WeatherArguments.class, arguments ->
                CompletableFuture.completedFuture(ToolExecutionResult.success(arguments.city() + ":" + arguments.unit())));

        assertEquals("get_weather", tool.definition().name());
        assertEquals("Gets the current weather for a city.", tool.definition().description());
        assertEquals(List.of("city"), tool.definition().inputSchema().get("required"));
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) Objects.requireNonNull(tool.definition().inputSchema().get("properties"));
        @SuppressWarnings("unchecked")
        Map<String, Object> city = (Map<String, Object>) Objects.requireNonNull(properties.get("city"));
        assertEquals("The city to look up.", city.get("description"));

        ToolExecutionResult result = tool.executor().execute(new ToolCall("call-1", "get_weather", Map.of("city", "Berlin")))
                .toCompletableFuture().join();
        assertEquals("Berlin:null", result.content());
        assertFalse(result.error());
    }

    @ToolName("get_weather")
    @ToolDescription("Gets the current weather for a city.")
    private record WeatherArguments(@ToolDescription("The city to look up.") String city, @Nullable String unit) { }
}
