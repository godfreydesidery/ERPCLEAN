package com.orbix.engine.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Module boundary and layering rules.
 *
 * Layout: each business module lives under com.orbix.engine.modules.&lt;m&gt;
 * with sub-packages domain.{entity,dto,enums,event}, service, repository.
 * REST controllers live in com.orbix.engine.api (one folder for every module).
 *
 * Rules enforced here:
 *   - controllers may not reach into any module's repository directly;
 *   - modules may not reach into another module's repository or entity;
 *   - layer order is controller -&gt; service -&gt; repository -&gt; domain.
 */
@AnalyzeClasses(packages = "com.orbix.engine",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule controllers_do_not_use_repositories = noClasses()
        .that().resideInAPackage("com.orbix.engine.api..")
        .should().dependOnClassesThat().resideInAPackage("..repository..")
        .because("controllers must go through services, not repositories directly");

    @ArchTest
    static final ArchRule modules_only_depend_on_published_dtos_or_infrastructure = classes()
        .that().resideInAPackage("com.orbix.engine.modules..")
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage(
            "com.orbix.engine.modules.common..",        // cross-cutting infrastructure
            "com.orbix.engine.modules.auth..",          // auth infrastructure (JWT, filter, security config)
            "com.orbix.engine.modules..domain.dto..",   // any module's published DTOs
            "com.orbix.engine.modules..domain.enums..", // and enums
            "java..", "jakarta..", "javax..",
            "org.springframework..", "org.hibernate..", "org.slf4j..",
            "com.fasterxml..", "lombok..", "io.jsonwebtoken..",
            "..domain..", "..service..", "..repository.."
        )
        .because("modules talk to each other only via published DTOs/enums or domain events");

    @ArchTest
    static final ArchRule layered_architecture = layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("controller").definedBy("com.orbix.engine.api..")
        .layer("service").definedBy("..modules..service..")
        .layer("repository").definedBy("..modules..repository..")
        .layer("domain").definedBy("..modules..domain..")
        .whereLayer("controller").mayNotBeAccessedByAnyLayer()
        .whereLayer("service").mayOnlyBeAccessedByLayers("controller")
        .whereLayer("repository").mayOnlyBeAccessedByLayers("service");
}
