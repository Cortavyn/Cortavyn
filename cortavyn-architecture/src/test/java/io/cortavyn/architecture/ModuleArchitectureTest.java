package io.cortavyn.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Enforces the dependency directions documented in {@code architecture/workspace.dsl}. */
@AnalyzeClasses(packages = "io.cortavyn", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {
    @ArchTest static final ArchRule core_is_independent = noClasses().that().resideInAnyPackage("io.cortavyn.core..").should().dependOnClassesThat().resideInAnyPackage("io.cortavyn.model..", "io.cortavyn.chat..", "io.cortavyn.graph..", "io.cortavyn.deep..", "io.cortavyn.provider..");
    @ArchTest static final ArchRule model_api_is_independent = noClasses().that().resideInAnyPackage("io.cortavyn.model..").should().dependOnClassesThat().resideInAnyPackage("io.cortavyn.core..", "io.cortavyn.chat..", "io.cortavyn.graph..", "io.cortavyn.deep..", "io.cortavyn.provider..");
    @ArchTest static final ArchRule providers_depend_only_on_model_api = noClasses().that().resideInAnyPackage("io.cortavyn.provider..").should().dependOnClassesThat().resideInAnyPackage("io.cortavyn.core..", "io.cortavyn.chat..", "io.cortavyn.graph..", "io.cortavyn.deep..");
    @ArchTest static final ArchRule chat_does_not_depend_on_higher_layers = noClasses().that().resideInAnyPackage("io.cortavyn.chat..").should().dependOnClassesThat().resideInAnyPackage("io.cortavyn.deep..", "io.cortavyn.provider..");
    @ArchTest static final ArchRule graph_does_not_depend_on_chat_or_deep = noClasses().that().resideInAnyPackage("io.cortavyn.graph..").should().dependOnClassesThat().resideInAnyPackage("io.cortavyn.chat..", "io.cortavyn.deep..", "io.cortavyn.provider..");
    @ArchTest static final ArchRule deep_does_not_depend_on_core_or_providers = noClasses().that().resideInAnyPackage("io.cortavyn.deep..").should().dependOnClassesThat().resideInAnyPackage("io.cortavyn.core..", "io.cortavyn.provider..");
}
