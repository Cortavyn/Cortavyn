package io.cortavyn.model.api;
/** Indicates that an assistant response could not be parsed as the requested structured type. */
public final class StructuredOutputException extends RuntimeException { public StructuredOutputException(String message, Throwable cause) { super(message, cause); } }
