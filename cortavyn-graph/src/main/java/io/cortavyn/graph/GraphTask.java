package io.cortavyn.graph;

import java.util.concurrent.CompletionStage;

/** Reusable asynchronous operation that can be wrapped by an application node and cached by key. */
@FunctionalInterface
public interface GraphTask<I, O> { CompletionStage<O> execute(I input); }
