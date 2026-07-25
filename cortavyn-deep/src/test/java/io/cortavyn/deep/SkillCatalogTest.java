package io.cortavyn.deep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SkillCatalogTest {
    @Test void parsesAgentSkillsFrontmatter() {
        DeepSkill skill = SkillCatalog.parse("---\nname: research\ndescription: Collect evidence\n---\nUse primary sources.");
        assertEquals("research", skill.name());
        assertEquals("Collect evidence", skill.description());
        assertEquals("Use primary sources.", skill.instructions());
    }
    @Test void rejectsMissingFrontmatter() { assertThrows(IllegalArgumentException.class, () -> SkillCatalog.parse("# no metadata")); }
    @Test void loadsResourcesBesideSkillInstructions(@org.junit.jupiter.api.io.TempDir Path root) throws Exception {
        Path skill = Files.createDirectories(root.resolve("research"));
        Files.writeString(skill.resolve("SKILL.md"), "---\nname: research\ndescription: Collect evidence\n---\nUse sources.");
        Files.writeString(skill.resolve("template.md"), "# Evidence");
        DeepSkill loaded = SkillCatalog.load(root).getFirst();
        assertEquals("# Evidence", loaded.resources().get("template.md"));
    }
}
