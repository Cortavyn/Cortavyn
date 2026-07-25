package io.cortavyn.model.api;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Function;
import java.util.function.Supplier;
import static java.nio.charset.StandardCharsets.UTF_8;

/** Internal transport support for line-oriented provider streaming protocols. */
public final class ChatStreamPublishers {
    private ChatStreamPublishers() { }

    /**
     * Sends an HTTP request and publishes lines from its response.  Parsing happens on the HTTP
     * client's executor, and {@link SubmissionPublisher} honours downstream demand.
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    public static Publisher<ChatStreamEvent> fromLines(
            HttpClient client, HttpRequest request, Function<HttpResponse<InputStream>, RuntimeException> error,
            Function<String, ? extends Iterable<ChatStreamEvent>> lineParser,
            Supplier<ChatCompletion> completion) {
        Objects.requireNonNull(client, "client must not be null");
        Objects.requireNonNull(request, "request must not be null");
        var publisher = new SubmissionPublisher<ChatStreamEvent>();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).whenComplete((response, failure) -> {
            if (failure != null) { publisher.closeExceptionally(failure); return; }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (InputStream body = response.body()) { publisher.closeExceptionally(error.apply(response)); }
                catch (Exception exception) { publisher.closeExceptionally(exception); }
                return;
            }
            try (var lines = new java.io.BufferedReader(new java.io.InputStreamReader(response.body(), UTF_8)).lines()) {
                lines.forEach(line -> lineParser.apply(line).forEach(publisher::submit));
                publisher.submit(completion.get());
                publisher.close();
            } catch (Throwable exception) { publisher.closeExceptionally(exception); }
        });
        return publisher;
    }

}
