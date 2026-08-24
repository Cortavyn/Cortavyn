package io.cortavyn.deep;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/** Isolated JVM entry point used to enforce the configured interpreter heap limit. */
public final class QuickJsInterpreterWorker {
    private QuickJsInterpreterWorker() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 4) throw new IllegalArgumentException("Expected interpreter limits as arguments");
        QuickJsInterpreterLimits limits = new QuickJsInterpreterLimits(
                Duration.ofMillis(Long.parseLong(args[0])),
                Long.parseLong(args[1]),
                Long.parseLong(args[2]),
                Integer.parseInt(args[3]));
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             BufferedWriter output = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
             QuickJsSession session = new QuickJsSession(limits)) {
            String line;
            while ((line = input.readLine()) != null) {
                DeepInterpreterResult result = session.eval(decode(line));
                write(output, encode(result.value()));
                write(output, result.error() == null ? "-" : encode(result.error()));
                write(output, Integer.toString(result.console().size()));
                for (DeepInterpreterResult.ConsoleMessage message : result.console()) {
                    write(output, message.level().name() + " " + encode(message.text()));
                }
                output.flush();
            }
        }
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(BufferedWriter output, String value) throws IOException {
        output.write(value);
        output.newLine();
    }
}
