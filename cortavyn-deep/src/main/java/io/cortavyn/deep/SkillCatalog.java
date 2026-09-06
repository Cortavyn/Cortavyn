package io.cortavyn.deep;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads a minimal Agent Skills {@code SKILL.md} catalogue without a YAML dependency. */
public final class SkillCatalog {
    private SkillCatalog() { }
    public static List<DeepSkill> load(Path root) throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        List<DeepSkill> skills = new ArrayList<>();
        // The two-level scan follows the Agent Skills layout: <root>/<skill>/SKILL.md.
        try (var paths = Files.walk(root, 2)) {
            for (Path path : paths.filter(candidate -> candidate.getFileName().toString().equals("SKILL.md")).toList()) {
                DeepSkill parsed = parse(Files.readString(path));
                java.util.Map<String, String> resources = new java.util.LinkedHashMap<>();
                Path directory = java.util.Objects.requireNonNull(path.getParent(), "SKILL.md must have a parent directory");
                // Resource paths stay relative to the skill directory, never to the host root.
                try (var files = Files.walk(directory)) {
                    for (Path file : files.filter(Files::isRegularFile).filter(file -> !file.equals(path)).toList()) resources.put(directory.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/"), Files.readString(file));
                }
                skills.add(new DeepSkill(parsed.name(), parsed.description(), parsed.instructions(), resources));
            }
        }
        return List.copyOf(skills);
    }
    /** Loads repository-level agent instructions, including nested directory guidance. */
    public static String loadAgentInstructions(Path root) throws IOException {
        if (!Files.isDirectory(root)) return "";
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.getFileName().toString().equals("AGENTS.md")).sorted().map(path -> {
                try { return "Instructions from " + root.relativize(path) + ":\n" + Files.readString(path); }
                catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }).collect(java.util.stream.Collectors.joining("\n\n"));
        } catch (java.io.UncheckedIOException failure) { throw failure.getCause(); }
    }
    public static DeepSkill parse(String markdown) {
        String[] sections = markdown.split("---", 3);
        if (sections.length < 3 || !sections[0].isBlank()) throw new IllegalArgumentException("SKILL.md requires YAML frontmatter");
        String name = value(sections[1], "name"); String description = value(sections[1], "description");
        return new DeepSkill(name, description, sections[2].strip());
    }
    private static String value(String frontmatter, String key) { return frontmatter.lines().filter(line -> line.startsWith(key + ":")).map(line -> line.substring(key.length() + 1).strip().replaceAll("^['\"]|['\"]$", "")).findFirst().orElseThrow(() -> new IllegalArgumentException("SKILL.md frontmatter missing " + key)); }
}
