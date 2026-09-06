package io.cortavyn.deep;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** One-request-at-a-time stdio MCP transport; process lifetime equals session lifetime. */
public final class StdioMcpTransport implements McpTransport {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Process process; private final BufferedReader input; private final BufferedWriter output;
    public StdioMcpTransport(List<String> command) {
        try { process = new ProcessBuilder(List.copyOf(Objects.requireNonNull(command, "command must not be null"))).start(); input = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)); output = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)); }
        catch (IOException failure) { throw new IllegalStateException("could not start MCP stdio server", failure); }
    }
    @Override public synchronized CompletionStage<JsonNode> request(JsonNode message) { return CompletableFuture.supplyAsync(() -> { try { output.write(message.toString()); output.newLine(); output.flush(); String line; while ((line = input.readLine()) != null) { JsonNode response = JSON.readTree(line); if (response.has("id") && response.get("id").equals(message.get("id"))) return response; } throw new IllegalStateException("MCP stdio server closed its output"); } catch (IOException failure) { throw new IllegalStateException("MCP stdio request failed", failure); } }); }
    @Override public void close() { process.destroyForcibly(); }
}
