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
                    new DeepInterpreterResult.ConsoleMessage(DeepInterpreterResult.Level.LOG, "hello {\"answer\":42}"),
                    new DeepInterpreterResult.ConsoleMessage(DeepInterpreterResult.Level.WARN, "careful")), logged.console());
            assertNull(logged.error());

            DeepInterpreterResult failed = interpreter.eval("thread", "throw new Error('bad input')").toCompletableFuture().join();
            assertNotNull(failed.error());
        }
    }

    @Test
    void enforcesExecutionAndOutputLimits() {
        QuickJsInterpreterLimits limits = new QuickJsInterpreterLimits(Duration.ofMillis(10), 8L * 1024 * 1024, 320L * 1024, 8);
        try (QuickJsInterpreter interpreter = new QuickJsInterpreter(limits)) {
            DeepInterpreterResult output = interpreter.eval("thread", "console.log('0123456789'); 'done'").toCompletableFuture().join();
            assertEquals("01234567", output.console().getFirst().text());

            DeepInterpreterResult timeout = interpreter.eval("thread", "while (true) { }").toCompletableFuture().join();
            assertNotNull(timeout.error());
            assertTrue(timeout.error().contains("execution timeout"));
        }
    }
}
