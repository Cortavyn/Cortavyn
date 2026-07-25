package io.cortavyn.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.cortavyn.model.api.ToolCall;
import io.cortavyn.model.api.ImageContent;
import io.cortavyn.model.api.TextContent;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.net.URI;
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

    @Test
    void givesRuntimeAwareToolsContextStoreAndProgressWriter() {
        var progress = new CopyOnWriteArrayList<ToolProgress>();
        var store = new InMemoryToolStore();
        ToolRuntime runtime = new ToolRuntime("run-1", Map.of("user", "Sebastian"), store, progress::add);
        ChatTool tool = ChatTool.typed("remember", "Stores a value.", RememberArguments.class, (arguments, toolRuntime) ->
                toolRuntime.store().write("memory", Map.of(arguments.key(), arguments.value()))
                        .thenApply(ignored -> {
                            toolRuntime.progressWriter().write(new ToolProgress("Stored " + arguments.key()));
                            return ToolExecutionResult.success("stored");
                        }));

        ToolExecutionResult result = tool.executor().execute(new ToolCall("call-2", "remember", Map.of("key", "name", "value", "Cortavyn")), runtime)
                .toCompletableFuture().join();

        assertEquals("stored", result.content());
        assertEquals(Map.of("name", "Cortavyn"), store.read("memory").toCompletableFuture().join());
        assertEquals("Stored name", progress.getFirst().message());
    }

    @Test
    void preservesStructuredAndMultimodalToolResults() {
        ToolExecutionResult result = ToolExecutionResult.success(List.of(
                new TextContent("Here is the report."),
                new ImageContent(URI.create("https://example.test/report.png"), "image/png")))
                .withMetadata(Map.of("source", "reporting"));

        assertEquals("Here is the report.", result.content());
        assertEquals(2, result.contentBlocks().size());
        assertEquals("reporting", result.metadata().get("source"));
    }

    @ToolName("get_weather")
    @ToolDescription("Gets the current weather for a city.")
    private record WeatherArguments(@ToolDescription("The city to look up.") String city, @Nullable String unit) { }

    private record RememberArguments(String key, String value) { }
}
