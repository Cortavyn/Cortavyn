package io.cortavyn.deep;
import java.util.Map;
import java.util.List;
/** A proposed action and permitted review decisions. */
public record ApprovalRequest(String toolName, Map<String, Object> arguments, List<ApprovalDecision.Type> allowed) { public ApprovalRequest { arguments = Map.copyOf(arguments); allowed = List.copyOf(allowed); } }
