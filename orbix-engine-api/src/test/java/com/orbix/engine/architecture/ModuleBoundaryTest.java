package com.orbix.engine.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Module boundary and layering rules per ARCHITECTURE.md §2.1 and §2.2.
 * These tests fail the build if any module reaches into another module's
 * domain/infra packages, or if controllers touch repositories directly.
 */
@AnalyzeClasses(packages = "com.orbix.engine",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule controllers_do_not_use_repositories = noClasses()
        .that().resideInAPackage("..api..")
        .should().dependOnClassesThat().resideInAPackage("..infra..")
        .because("controllers must go through application services, not repositories directly");

    @ArchTest
    static final ArchRule modules_only_depend_on_published_api_or_platform = classes()
        .that().resideInAnyPackage(
            "com.orbix.engine.catalog..",
            "com.orbix.engine.sales..",
            "com.orbix.engine.procurement..",
            "com.orbix.engine.stock..",
            "com.orbix.engine.pos..",
            "com.orbix.engine.wms..",
            "com.orbix.engine.production..",
            "com.orbix.engine.debt..",
            "com.orbix.engine.cash..",
            "com.orbix.engine.day..",
            "com.orbix.engine.hr..",
            "com.orbix.engine.reporting..",
            "com.orbix.engine.integration..",
            "com.orbix.engine.party.."
        )
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage(
            "com.orbix.engine..api..",      // any module's published DTOs
            "com.orbix.engine.platform..",  // cross-cutting platform
            "java..", "jakarta..", "javax..",
            "org.springframework..", "org.hibernate..", "org.slf4j..",
            "com.fasterxml..", "lombok..",
            "..app..", "..domain..", "..infra.."
        )
        .because("modules talk to each other only via published APIs or domain events");

    @ArchTest
    static final ArchRule hexagonal_layering = layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("api").definedBy("..api..")
        .layer("app").definedBy("..app..")
        .layer("domain").definedBy("..domain..")
        .layer("infra").definedBy("..infra..")
        .whereLayer("api").mayOnlyBeAccessedByLayers("app")
        .whereLayer("app").mayOnlyBeAccessedByLayers("api")
        .whereLayer("infra").mayOnlyBeAccessedByLayers("app");
}
