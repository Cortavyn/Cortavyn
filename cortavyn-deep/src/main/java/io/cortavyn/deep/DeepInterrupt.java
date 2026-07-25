package io.cortavyn.deep;
import java.util.List;
/** Pending sensitive actions requiring a human decision. */
public record DeepInterrupt(List<ApprovalRequest> actions) { public DeepInterrupt { actions = List.copyOf(actions); } }
