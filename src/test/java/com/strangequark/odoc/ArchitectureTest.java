package com.strangequark.odoc;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Guardrails for the modular-monolith boundaries established in Phase 0. */
@AnalyzeClasses(packages = "com.strangequark.odoc", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule controllersStayAtTheHttpBoundary = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAnyPackage(
                    "..auth..", "..page..", "..space..", "..workspace..", "..media..", "..github..", "..commentary..", "..system..")
            .because("HTTP adapters belong to their owning module, not the shared configuration layer");

    @ArchTest
    static final ArchRule configurationDoesNotDependOnFeatureModules = noClasses()
            .that().resideInAPackage("..config..")
            .should().dependOnClassesThat().resideInAnyPackage("..page..", "..space..", "..media..", "..github..", "..commentary..");
}
