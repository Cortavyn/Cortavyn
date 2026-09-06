package io.cortavyn.examples.deep;

import io.cortavyn.deep.DeepInterpreterTool;
import io.cortavyn.deep.QuickJsInterpreter;
import io.cortavyn.deep.QuickJsInterpreterLimits;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Demonstrates explicitly allowlisted programmatic tool calling from isolated QuickJS. */
public final class InterpreterToolsExample {
    private InterpreterToolsExample() { }
    public static void main(String[] arguments) {
        DeepInterpreterTool calculator = new DeepInterpreterTool() {
            @Override public String name() { return "constant"; }
            @Override public java.util.concurrent.CompletionStage<String> call(String input) { return CompletableFuture.completedFuture("{\"answer\":42}"); }
        };
        try (QuickJsInterpreter interpreter = new QuickJsInterpreter(QuickJsInterpreterLimits.defaults(), List.of(calculator))) {
            System.out.println(interpreter.eval("example", "tools.constant({}).answer").toCompletableFuture().join());
        }
    }
}
