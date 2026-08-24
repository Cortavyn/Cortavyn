package io.cortavyn.deep;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSRuntime;
import com.caoccao.qjs4j.core.JSRuntimeOptions;
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
        context.getGlobalObject().set("__cortavynResult", value);
        try {
            return String.valueOf(context.eval("globalThis.__cortavynRender(globalThis.__cortavynResult)").toJavaObject());
        } finally {
            context.eval("globalThis.__cortavynResult = undefined;");
        }
    }

    private static long deadline(Duration timeout) {
        try { return Math.addExact(System.currentTimeMillis(), timeout.toMillis()); }
        catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
    }

    private static String message(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
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
                Object.defineProperty(globalThis, "__cortavynRender", {
                  configurable: false,
                  writable: false,
                  value: (() => {
                    const maxEntries = 64;
                    const maxString = %d;
                    const objectToString = Object.prototype.toString;
                    const clipped = value => value.length <= maxString ? value : value.slice(0, maxString) + "…";
                    const tag = value => objectToString.call(value);
                    const describe = (value, nested) => {
                      const type = typeof value;
                      if (type === "undefined") return { type, value: "undefined" };
                      if (type === "string") return { type, value: clipped(value) };
                      if (type === "number") return { type, value: Number.isFinite(value) ? value : null };
                      if (type === "boolean") return { type, value };
                      if (type === "bigint" || type === "symbol" || type === "function") return { type, value: tag(value) };
                      if (value === null) return { type: "object", value: null };
                      if (nested) return { type: "object", value: tag(value) };
                      if (Array.isArray(value)) {
                        const count = Math.min(value.length, maxEntries);
                        const items = [];
                        for (let index = 0; index < count; index++) items.push(describe(value[index], true));
                        return { type: "object", value: items, truncated: value.length > count };
                      }
                      const keys = Object.keys(value);
                      const result = {};
                      for (const key of keys.slice(0, maxEntries)) result[key] = describe(value[key], true);
                      return { type: "object", value: result, truncated: keys.length > maxEntries };
                    };
                    return value => JSON.stringify(describe(value, false));
                  })()
                });
                """.formatted(maxOutputCharacters, Math.max(0, maxOutputCharacters - 512));
    }
}
