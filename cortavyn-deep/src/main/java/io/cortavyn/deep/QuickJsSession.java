package io.cortavyn.deep;

import com.caoccao.qjs4j.core.JSBoolean;
import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSNull;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSRuntime;
import com.caoccao.qjs4j.core.JSRuntimeOptions;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSUndefined;
import com.caoccao.qjs4j.core.JSValue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The JavaScript engine state that lives only inside an isolated worker JVM. */
final class QuickJsSession implements AutoCloseable {
    private final QuickJsInterpreterLimits limits;
    private final JSRuntime runtime;
    private final JSContext context;

    QuickJsSession(QuickJsInterpreterLimits limits) {
        this.limits = limits;
        runtime = new JSRuntime(new JSRuntimeOptions().setMaxStackSize(limits.maxStackBytes()));
        context = runtime.createContext();
        context.eval(bootstrap(limits.maxOutputCharacters()));
    }

    DeepInterpreterResult eval(String code) {
        context.getVirtualMachine().setExecutionDeadline(deadline(limits.executionTimeout()));
        context.eval("globalThis.__cortavynConsole = []; globalThis.__cortavynConsoleSize = 0;");
        try {
            return DeepInterpreterResult.success(render(context.eval(code)), console());
        } catch (RuntimeException failure) {
            return DeepInterpreterResult.failure(message(failure), console());
        } finally {
            context.getVirtualMachine().setExecutionDeadline(0);
        }
    }

    private List<DeepInterpreterResult.ConsoleMessage> console() {
        Object value = context.getGlobalObject().get("__cortavynConsole").toJavaObject();
        if (!(value instanceof List<?> entries)) return List.of();
        List<DeepInterpreterResult.ConsoleMessage> result = new ArrayList<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> values)) continue;
            try {
                result.add(new DeepInterpreterResult.ConsoleMessage(
                        DeepInterpreterResult.Level.valueOf(String.valueOf(values.get("level"))), String.valueOf(values.get("text"))));
            } catch (IllegalArgumentException ignored) {
                // Script code can mutate the capture array; malformed entries are not console output.
            }
        }
        return List.copyOf(result);
    }

    private String render(JSValue value) {
        if (value instanceof JSUndefined) return "{\"type\":\"undefined\",\"value\":\"undefined\"}";
        if (value instanceof JSNull) return "{\"type\":\"object\",\"value\":null}";
        if (value instanceof JSBoolean || value instanceof JSNumber) {
            return "{\"type\":\"" + value.type().name().toLowerCase() + "\",\"value\":" + value + "}";
        }
        if (value instanceof JSString string) {
            return "{\"type\":\"string\",\"value\":" + quote(truncate(string.value())) + "}";
        }
        return "{\"type\":\"" + value.type().name().toLowerCase() + "\",\"value\":"
                + quote("[" + value.getClass().getSimpleName() + "]") + "}";
    }

    private String truncate(String value) {
        return value.length() <= limits.maxOutputCharacters() ? value : value.substring(0, limits.maxOutputCharacters()) + "…";
    }

    private static long deadline(Duration timeout) {
        try { return Math.addExact(System.currentTimeMillis(), timeout.toMillis()); }
        catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
    }

    private static String message(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('\"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) result.append("\\u%04x".formatted((int) character));
                    else result.append(character);
                }
            }
        }
        return result.append('\"').toString();
    }

    @Override public void close() {
        context.close();
        runtime.close();
    }

    private static String bootstrap(int maxOutputCharacters) {
        return """
                globalThis.__cortavynConsole = [];
                globalThis.__cortavynConsoleSize = 0;
                globalThis.__cortavynStringify = value => {
                  try {
                    if (typeof value === "string") return value;
                    if (value && typeof value === "object") return Object.prototype.toString.call(value);
                    return String(value);
                  } catch (_) { return "[unprintable]"; }
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
