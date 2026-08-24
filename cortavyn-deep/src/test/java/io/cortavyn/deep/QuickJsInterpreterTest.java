package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuickJsInterpreterTest {

    @Test
    void invokesOnlyExplicitlyAllowlistedProgrammaticTools() {
        DeepInterpreterTool echo = new DeepInterpreterTool() {
            @Override public String name() { return "echo"; }
            @Override public java.util.concurrent.CompletionStage<String> call(String argumentsJson) { return java.util.concurrent.CompletableFuture.completedFuture("{\"answer\":42}"); }
        };
        try (QuickJsInterpreter interpreter = new QuickJsInterpreter(QuickJsInterpreterLimits.defaults(), List.of(echo))) {
            assertEquals("{\"type\":\"number\",\"value\":42}", interpreter.eval("thread", "tools.echo({ value: 1 }).answer").toCompletableFuture().join().value());
            assertTrue(interpreter.eval("thread", "typeof tools.notAllowed").toCompletableFuture().join().value().contains("undefined"));
        }
    }
    @Test
    void preservesStatePerThreadAndIsolatesOtherThreads() {
        try (QuickJsInterpreter interpreter = new QuickJsInterpreter()) {
            assertNull(interpreter.eval("first", "var value = 41; value").toCompletableFuture().join().error());
            assertEquals("{\"type\":\"number\",\"value\":42}", interpreter.eval("first", "value + 1").toCompletableFuture().join().value());
            DeepInterpreterResult isolated = interpreter.eval("second", "typeof value").toCompletableFuture().join();
            assertEquals("{\"type\":\"string\",\"value\":\"undefined\"}", isolated.value());
        }
    }

    @Test
    void capturesConsoleOutputAndScriptFailures() {
        try (QuickJsInterpreter interpreter = new QuickJsInterpreter()) {
            DeepInterpreterResult logged = interpreter.eval("thread", "console.log('hello', { answer: 42 }); console.warn('careful'); 7").toCompletableFuture().join();
            assertEquals("{\"type\":\"number\",\"value\":7}", logged.value());
            assertEquals(List.of(
                    new DeepInterpreterResult.ConsoleMessage(DeepInterpreterResult.Level.LOG, "hello [object Object]"),
                    new DeepInterpreterResult.ConsoleMessage(DeepInterpreterResult.Level.WARN, "careful")), logged.console());
            assertNull(logged.error());

            DeepInterpreterResult failed = interpreter.eval("thread", "throw new Error('bad input')").toCompletableFuture().join();
            assertNotNull(failed.error());
        }
    }

    @Test
    void safelyFormatsStructuredAndNonSerializableResults() {
        try (QuickJsInterpreter interpreter = new QuickJsInterpreter()) {
            DeepInterpreterResult structured = interpreter.eval("thread", "({ answer: 42, nested: [true, 'ok'] })").toCompletableFuture().join();
            assertEquals("{\"type\":\"object\",\"value\":{\"answer\":{\"type\":\"number\",\"value\":42},\"nested\":{\"type\":\"object\",\"value\":\"[object Array]\"}},\"truncated\":false}", structured.value());

            DeepInterpreterResult function = interpreter.eval("thread", "() => 42").toCompletableFuture().join();
            assertEquals("{\"type\":\"function\",\"value\":\"[object Function]\"}", function.value());
        }
    }

    @Test
    void enforcesExecutionAndOutputLimits() {
        QuickJsInterpreterLimits limits = new QuickJsInterpreterLimits(Duration.ofMillis(10), 64L * 1024 * 1024, 320L * 1024, 8);
        try (QuickJsInterpreter interpreter = new QuickJsInterpreter(limits)) {
            DeepInterpreterResult output = interpreter.eval("thread", "console.log('0123456789'); 'done'").toCompletableFuture().join();
            assertEquals("01234567", output.console().getFirst().text());

            DeepInterpreterResult timeout = interpreter.eval("thread", "while (true) { }").toCompletableFuture().join();
            assertNotNull(timeout.error());
            assertTrue(timeout.error().contains("execution timeout"));
        }
    }

    @Test
    void enforcesTheRuntimeMemoryLimit() {
        QuickJsInterpreterLimits limits = new QuickJsInterpreterLimits(Duration.ofSeconds(1), 32L * 1024 * 1024, 320L * 1024, 1_000);
        try (QuickJsInterpreter interpreter = new QuickJsInterpreter(limits)) {
            DeepInterpreterResult result = interpreter.eval("thread", "new Uint8Array(64 * 1024 * 1024)").toCompletableFuture().join();
            assertNotNull(result.error());
        }
    }

    @Test
    void exposesNoJavaOrHostCapabilities() {
        try (QuickJsInterpreter interpreter = new QuickJsInterpreter()) {
            DeepInterpreterResult result = interpreter.eval("thread", "[typeof Java, typeof Packages, typeof process, typeof require, typeof ProcessSandbox].join(',')")
                    .toCompletableFuture().join();

            assertEquals("{\"type\":\"string\",\"value\":\"undefined,undefined,undefined,undefined,undefined\"}", result.value());
            assertNull(result.error());
        }
    }
}
