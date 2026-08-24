package io.cortavyn.deep;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSRuntime;
import com.caoccao.qjs4j.core.JSRuntimeOptions;
import com.caoccao.qjs4j.core.JSValue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Thread-scoped, resource-limited QuickJS interpreter with no Java host bindings. */
public final class QuickJsInterpreter implements DeepInterpreter {
    private final QuickJsInterpreterLimits limits;
    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public QuickJsInterpreter() { this(QuickJsInterpreterLimits.defaults()); }

    public QuickJsInterpreter(QuickJsInterpreterLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits must not be null");
    }

    @Override public CompletionStage<DeepInterpreterResult> eval(String threadId, String code) {
        Objects.requireNonNull(threadId, "threadId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        return CompletableFuture.supplyAsync(() -> sessions.computeIfAbsent(threadId, ignored -> new Session(limits)).eval(code), executor);
    }

    @Override public void closeThread(String threadId) {
        Session session = sessions.remove(Objects.requireNonNull(threadId, "threadId must not be null"));
        if (session != null) session.close();
    }

    @Override public void close() {
        sessions.forEach((threadId, session) -> session.close());
        sessions.clear();
        executor.close();
    }

    private static final class Session implements AutoCloseable {
        private final QuickJsInterpreterLimits limits;
        private final JSRuntime runtime;
        private final JSContext context;

        private Session(QuickJsInterpreterLimits limits) {
            this.limits = limits;
            runtime = new JSRuntime(new JSRuntimeOptions()
                    .setMaxMemoryUsage(limits.maxMemoryBytes())
                    .setMaxStackSize(limits.maxStackBytes()));
            context = runtime.createContext();
            context.eval(bootstrap(limits.maxOutputCharacters()));
        }

        private synchronized DeepInterpreterResult eval(String code) {
            context.getVirtualMachine().setExecutionDeadline(deadline(limits.executionTimeout()));
            context.eval("globalThis.__cortavynConsole = []; globalThis.__cortavynConsoleSize = 0;");
            try {
                JSValue value = context.eval(code);
                return DeepInterpreterResult.success(render(value), console());
            } catch (RuntimeException failure) {
                return DeepInterpreterResult.failure(message(failure), console());
            } finally {
                context.getVirtualMachine().setExecutionDeadline(0);
            }
        }

        private String render(JSValue value) {
            context.getGlobalObject().set("__cortavynResult", value);
            try {
                Object serialized = context.eval("""
                        (() => {
                          const value = globalThis.__cortavynResult;
                          try {
                            const serialized = JSON.stringify(value);
                            return serialized === undefined
                              ? JSON.stringify({ type: typeof value, value: String(value) })
                              : JSON.stringify({ type: typeof value, value: JSON.parse(serialized) });
                          } catch (_) {
                            return JSON.stringify({ type: typeof value, value: Object.prototype.toString.call(value) });
                          }
                        })()
                        """).toJavaObject();
                return truncate(String.valueOf(serialized));
            } finally {
                context.eval("globalThis.__cortavynResult = undefined;");
            }
        }

        private List<DeepInterpreterResult.ConsoleMessage> console() {
            Object value = context.getGlobalObject().get("__cortavynConsole").toJavaObject();
            if (!(value instanceof List<?> entries)) return List.of();
            List<DeepInterpreterResult.ConsoleMessage> result = new ArrayList<>();
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> values)) continue;
                try {
                    DeepInterpreterResult.Level level = DeepInterpreterResult.Level.valueOf(String.valueOf(values.get("level")));
                    result.add(new DeepInterpreterResult.ConsoleMessage(level, String.valueOf(values.get("text"))));
                } catch (IllegalArgumentException ignored) {
                    // Script code can mutate the capture array; malformed entries are not console output.
                }
            }
            return List.copyOf(result);
        }

        private String truncate(String text) {
            return text.length() <= limits.maxOutputCharacters() ? text : text.substring(0, limits.maxOutputCharacters()) + "…";
        }

        @Override public synchronized void close() {
            context.close();
            runtime.close();
        }

        private static long deadline(Duration timeout) {
            try {
                return Math.addExact(System.currentTimeMillis(), timeout.toMillis());
            } catch (ArithmeticException ignored) {
                return Long.MAX_VALUE;
            }
        }

        private static String message(RuntimeException failure) {
            String message = failure.getMessage();
            return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
        }

        private static String bootstrap(int maxOutputCharacters) {
            return """
                    globalThis.__cortavynConsole = [];
                    globalThis.__cortavynConsoleSize = 0;
                    globalThis.__cortavynStringify = value => {
                      try {
                        if (typeof value === "string") return value;
                        const serialized = JSON.stringify(value);
                        return serialized === undefined ? String(value) : serialized;
                      } catch (_) { return Object.prototype.toString.call(value); }
                    };
                    globalThis.__cortavynWrite = (level, values) => {
                      const text = values.map(globalThis.__cortavynStringify).join(" ");
                      const remaining = %d - globalThis.__cortavynConsoleSize;
                      if (remaining <= 0) return;
                      const clipped = text.slice(0, remaining);
                      globalThis.__cortavynConsole.push({ level, text: clipped });
                      globalThis.__cortavynConsoleSize += clipped.length;
                    };
                    globalThis.console = {
                      log: (...values) => globalThis.__cortavynWrite("LOG", values),
                      warn: (...values) => globalThis.__cortavynWrite("WARN", values),
                      error: (...values) => globalThis.__cortavynWrite("ERROR", values)
                    };
                    """.formatted(maxOutputCharacters);
        }
    }
}
