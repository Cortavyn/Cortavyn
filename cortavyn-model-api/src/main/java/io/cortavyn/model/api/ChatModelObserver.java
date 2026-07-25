package io.cortavyn.model.api;

/** Non-invasive callback boundary for tracing, metrics, and audit exporters. */
public interface ChatModelObserver {
    default void onStart(ChatRequest request) { }
    default void onComplete(ModelCallEvent event) { }
}
