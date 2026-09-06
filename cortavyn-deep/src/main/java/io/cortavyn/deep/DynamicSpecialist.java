package io.cortavyn.deep;

/** Runtime-defined specialist with an isolated system prompt and inherited safe defaults. */
public record DynamicSpecialist(String name, String description, String systemPrompt) {
    public DynamicSpecialist { if (name == null || name.isBlank() || !name.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("name must contain only letters, numbers, '_' or '-'"); if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank"); if (systemPrompt == null || systemPrompt.isBlank()) throw new IllegalArgumentException("systemPrompt must not be blank"); }
}
