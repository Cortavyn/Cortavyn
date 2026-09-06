package io.cortavyn.deep;

import com.caoccao.qjs4j.core.JSRuntime;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Thread-scoped, resource-limited QuickJS interpreter with no Java host bindings. */
public final class QuickJsInterpreter implements DeepInterpreter {
    private final QuickJsInterpreterLimits limits;
    private final java.util.Map<String, DeepInterpreterTool> tools;
    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public QuickJsInterpreter() { this(QuickJsInterpreterLimits.defaults(), List.of()); }

    public QuickJsInterpreter(QuickJsInterpreterLimits limits) { this(limits, List.of()); }
    /** Enables only the explicitly provided programmatic tools; absent tools remain unreachable. */
    public QuickJsInterpreter(QuickJsInterpreterLimits limits, List<DeepInterpreterTool> tools) {
        this.limits = Objects.requireNonNull(limits, "limits must not be null");
        this.tools = tools.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(DeepInterpreterTool::name, tool -> tool));
    }

    @Override public CompletionStage<DeepInterpreterResult> eval(String threadId, String code) {
        Objects.requireNonNull(threadId, "threadId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        CompletableFuture<DeepInterpreterResult> evaluation = CompletableFuture.supplyAsync(() -> {
            try {
                return sessions.computeIfAbsent(threadId, ignored -> new Session(limits, tools.keySet())).eval(code, tools);
            } catch (RuntimeException failure) {
                return DeepInterpreterResult.failure("Could not start JavaScript worker: " + Session.message(failure), List.of());
            }
        }, executor);
        return evaluation.orTimeout(timeoutMillis(limits.executionTimeout()), TimeUnit.MILLISECONDS).exceptionally(failure -> {
            closeThread(threadId);
            return DeepInterpreterResult.failure("JavaScript execution timeout", List.of());
        });
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
        private final Process process;
        private final BufferedWriter writer;
        private final BufferedReader reader;
        private final BufferedReader errorReader;

        private Session(QuickJsInterpreterLimits limits, java.util.Set<String> toolNames) {
            try {
                process = new ProcessBuilder(
                        javaExecutable(),
                        "-Xmx" + limits.maxMemoryBytes(),
                        "-cp",
                        workerClassPath(),
                        QuickJsInterpreterWorker.class.getName(),
                        Long.toString(limits.executionTimeout().toMillis()),
                        Long.toString(limits.maxMemoryBytes()),
                        Long.toString(limits.maxStackBytes()),
                        Integer.toString(limits.maxOutputCharacters()),
                        String.join(",", toolNames))
                        .start();
                writer = process.outputWriter(StandardCharsets.UTF_8);
                reader = process.inputReader(StandardCharsets.UTF_8);
                errorReader = process.errorReader(StandardCharsets.UTF_8);
            } catch (IOException failure) {
                throw new IllegalStateException("Could not start the isolated JavaScript worker", failure);
            }
        }

        private synchronized DeepInterpreterResult eval(String code, java.util.Map<String, DeepInterpreterTool> tools) {
            try {
                writer.write(encode(code));
                writer.newLine();
                writer.flush();
                String value = read("result value");
                while (value.startsWith("CALL ")) {
                    String[] call = value.split(" ", 3);
                    DeepInterpreterTool tool = call.length == 3 ? tools.get(call[1]) : null;
                    String response = tool == null ? "{\"error\":\"tool not allowed\"}" : tool.call(decode(call[2])).toCompletableFuture().join();
                    writer.write("RETURN " + encode(response)); writer.newLine(); writer.flush();
                    value = read("result value");
                }
                String error = read("result error");
                int consoleCount = Integer.parseInt(read("console size"));
                List<DeepInterpreterResult.ConsoleMessage> console = new java.util.ArrayList<>(consoleCount);
                for (int index = 0; index < consoleCount; index++) {
                    String[] entry = read("console entry").split(" ", 2);
                    console.add(new DeepInterpreterResult.ConsoleMessage(
                            DeepInterpreterResult.Level.valueOf(entry[0]), decode(entry.length == 2 ? entry[1] : "")));
                }
                return error.equals("-")
                        ? DeepInterpreterResult.success(decode(value), console)
                        : DeepInterpreterResult.failure(decode(error), console);
            } catch (IOException | IllegalArgumentException failure) {
                return DeepInterpreterResult.failure(workerFailure(failure), List.of());
            }
        }

        private String read(String part) throws IOException {
            String line = reader.readLine();
            if (line == null) throw new IOException("JavaScript worker terminated while reading " + part);
            return line;
        }

        private String workerFailure(Exception failure) {
            if (!process.isAlive()) {
                return "JavaScript worker stopped (exit code " + process.exitValue() + "): " + errorOutput();
            }
            return "Could not read JavaScript worker result: " + failure.getMessage();
        }

        private static String javaExecutable() {
            return Path.of(System.getProperty("java.home"), "bin", "java").toString();
        }

        private static String workerClassPath() {
            return classLocation(QuickJsInterpreter.class) + java.io.File.pathSeparator + classLocation(JSRuntime.class);
        }

        private static String classLocation(Class<?> type) {
            try {
                return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            } catch (URISyntaxException failure) {
                throw new IllegalStateException("Could not resolve the JavaScript worker classpath", failure);
            }
        }

        @Override public void close() {
            try {
                writer.close();
                reader.close();
                errorReader.close();
            } catch (IOException ignored) {
                // The process may already have been terminated by a resource limit.
            }
            process.destroy();
            if (process.isAlive()) process.destroyForcibly();
        }

        private static String encode(String value) {
            return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }

        private static String decode(String value) {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        }

        private String errorOutput() {
            try {
                String error = errorReader.readLine();
                return error == null || error.isBlank() ? "the script may have exceeded its memory limit" : error;
            } catch (IOException ignored) {
                return "the script may have exceeded its memory limit";
            }
        }

        private static String message(RuntimeException failure) {
            String message = failure.getMessage();
            return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
        }
    }

    private static long timeoutMillis(Duration timeout) {
        try {
            return Math.addExact(timeout.toMillis(), 1_000L);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
