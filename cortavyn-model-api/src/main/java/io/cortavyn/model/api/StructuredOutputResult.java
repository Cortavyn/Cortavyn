package io.cortavyn.model.api;
import java.util.Objects;
/** A parsed structured value together with the provider response that produced it. */
public record StructuredOutputResult<T>(T value, ChatResponse response) { public StructuredOutputResult { Objects.requireNonNull(value); Objects.requireNonNull(response); } }
