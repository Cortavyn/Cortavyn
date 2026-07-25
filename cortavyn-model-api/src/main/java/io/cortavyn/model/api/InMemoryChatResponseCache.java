package io.cortavyn.model.api;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Unbounded thread-safe cache intended for tests and small-lived processes. */
public final class InMemoryChatResponseCache implements ChatResponseCache {
    private final ConcurrentHashMap<ChatRequest, ChatResponse> entries = new ConcurrentHashMap<>();
    @Override public Optional<ChatResponse> get(ChatRequest request) { return Optional.ofNullable(entries.get(request)); }
    @Override public void put(ChatRequest request, ChatResponse response) { entries.put(request, response); }
    public void clear() { entries.clear(); }
    public int size() { return entries.size(); }
}
