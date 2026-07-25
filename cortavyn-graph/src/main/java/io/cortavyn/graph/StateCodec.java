package io.cortavyn.graph;

/** Application-provided codec used by persistent {@link CheckpointStore} implementations. */
public interface StateCodec<S> { byte[] encode(S state); S decode(byte[] bytes); }
