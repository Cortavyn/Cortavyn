package io.cortavyn.examples.deep;

import io.cortavyn.deep.QuickJsInterpreter;

/** Runs isolated JavaScript and demonstrates that state is scoped to one agent thread. */
public final class QuickJsInterpreterExample {
    private QuickJsInterpreterExample() { }

    public static void main(String[] arguments) {
        try (QuickJsInterpreter interpreter = new QuickJsInterpreter()) {
            System.out.println(interpreter.eval("analysis", "var total = 40; console.log('seeded', total); total").toCompletableFuture().join());
            System.out.println(interpreter.eval("analysis", "total + 2").toCompletableFuture().join());
            System.out.println(interpreter.eval("independent", "typeof total").toCompletableFuture().join());
        }
    }
}
