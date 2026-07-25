package io.cortavyn.model.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

class ChatStreamPublishersTest {
    @Test void respectsDownstreamDemandBeforeDeliveringFurtherEvents() throws Exception {
        try (Server server = new Server("one\ntwo\n")) {
            var events = new CompletableFuture<List<ChatStreamEvent>>();
            var received = new java.util.ArrayList<ChatStreamEvent>();
            ChatStreamPublishers.fromLines(HttpClient.newHttpClient(), server.request(), response -> new IllegalStateException(),
                    line -> List.of(new ChatTextDelta(line)), () -> new ChatCompletion(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, "done"))))
                    .subscribe(new Flow.Subscriber<>() {
                        private Flow.@org.jspecify.annotations.Nullable Subscription subscription;
                        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; subscription.request(1); }
                        @Override public void onNext(ChatStreamEvent item) { received.add(item); if (received.size() == 1) java.util.Objects.requireNonNull(subscription).request(Long.MAX_VALUE); }
                        @Override public void onError(Throwable error) { events.completeExceptionally(error); }
                        @Override public void onComplete() { events.complete(List.copyOf(received)); }
                    });
            assertEquals(3, events.get(5, TimeUnit.SECONDS).size());
        }
    }

    @Test void cancellationStopsFurtherDelivery() throws Exception {
        try (Server server = new Server("one\ntwo\nthree\n")) {
            var delivered = new CompletableFuture<Integer>(); var count = new AtomicInteger();
            ChatStreamPublishers.fromLines(HttpClient.newHttpClient(), server.request(), response -> new IllegalStateException(),
                    line -> List.of(new ChatTextDelta(line)), () -> new ChatCompletion(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, "done"))))
                    .subscribe(new Flow.Subscriber<>() {
                        private Flow.@org.jspecify.annotations.Nullable Subscription subscription;
                        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; subscription.request(Long.MAX_VALUE); }
                        @Override public void onNext(ChatStreamEvent item) { if (count.incrementAndGet() == 1) { java.util.Objects.requireNonNull(subscription).cancel(); delivered.complete(count.get()); } }
                        @Override public void onError(Throwable error) { delivered.completeExceptionally(error); }
                        @Override public void onComplete() { }
                    });
            assertEquals(1, delivered.get(5, TimeUnit.SECONDS));
        }
    }

    private static final class Server implements AutoCloseable {
        private final HttpServer server;
        Server(String response) throws Exception { try { server = HttpServer.create(new InetSocketAddress("localhost", 0), 0); } catch (java.net.SocketException exception) { throw new TestAbortedException("local socket binding is unavailable", exception); } server.createContext("/", exchange -> { byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8); exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close(); }); server.start(); }
        HttpRequest request() { return HttpRequest.newBuilder(URI.create("http://localhost:" + server.getAddress().getPort() + "/")).GET().build(); }
        @Override public void close() { server.stop(0); }
    }
}
