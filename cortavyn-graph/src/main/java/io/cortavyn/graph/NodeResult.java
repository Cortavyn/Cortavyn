package io.cortavyn.graph;

/** Result of a node invocation. */
public sealed interface NodeResult permits StateUpdate, Command, Send, Sends, Interrupt { StateUpdate update(); }
