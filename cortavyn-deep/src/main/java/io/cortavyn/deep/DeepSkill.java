package io.cortavyn.deep;

import java.util.Objects;
import java.util.Map;

/** Agent-Skills-compatible metadata and deferred instructions. */
public record DeepSkill(String name, String description, String instructions, Map<String, String> resources) {
    public DeepSkill { if (name == null || name.isBlank()) throw new IllegalArgumentException("skill name must not be blank"); Objects.requireNonNull(description, "description must not be null"); Objects.requireNonNull(instructions, "instructions must not be null"); resources = Map.copyOf(resources); }
    public DeepSkill(String name, String description, String instructions) { this(name, description, instructions, Map.of()); }
}
