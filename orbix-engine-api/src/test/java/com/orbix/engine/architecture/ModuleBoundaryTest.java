package com.orbix.engine.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Module boundary and layering rules.
 *
 * Layout: each business module lives under {@code com.orbix.engine.modules.<m>}
 * with sub-packages {@code domain.{entity,dto,enums,event}}, {@code service},
 * {@code repository}. REST controllers live in {@code com.orbix.engine.api}
 * (one folder for every module).
 *
 * <p>Rules enforced here:
 * <ul>
 *   <li>controllers may not reach into any module's repository directly;</li>
 *   <li>modules may not reach into another module's repository or entity;</li>
 *   <li>layer order is controller -&gt; service -&gt; repository -&gt; domain;</li>
 *   <li>cross-module reach from {@code procurement} into {@code stock} is
 *       restricted to {@code stock.service.*Service} interfaces only
 *       (named exemption — ADR-0003).</li>
 * </ul>
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
            "com.orbix.engine.modules.iam..",           // identity + access management (users, roles, permissions)
            "com.orbix.engine.modules..domain.dto..",   // any module's published DTOs
            "com.orbix.engine.modules..domain.enums..", // and enums
            "java..", "jakarta..", "javax..",
            "org.springframework..", "org.hibernate..", "org.slf4j..",
            "org.aspectj..",                            // AOP infrastructure (AuditAspect)
            "com.fasterxml..", "lombok..", "io.jsonwebtoken..",
            "com.github.f4b6a3..",                      // ULID library used by common.util.UidGenerator
            "..domain..", "..service..", "..repository.."
        )
        .because("modules talk to each other only via published DTOs/enums or domain events "
            + "(the broad ..service.. allowance is tightened by the procurement→stock rule below)");

    /**
     * ADR-0003 — the procurement module may invoke the stock module synchronously
     * inside the GRN-post transaction, but only through {@code stock.service.*Service}
     * interfaces. Reaches into {@code stock.service.*Impl}, {@code stock.repository..}
     * or {@code stock.domain.entity..} are forbidden — those are the seam the ADR
     * declares as off-limits.
     *
     * <p>This rule is the encoded form of ADR-0003. Adding another module to the
     * exemption requires a new ADR and an explicit branch here.
     */
    @ArchTest
    static final ArchRule procurement_may_only_call_stock_service_interfaces =
        classes()
            .that().resideInAPackage("com.orbix.engine.modules.procurement..")
            .should(reachStockOnlyViaServiceInterfaces())
            .because("ADR-0003 — procurement → stock is restricted to *Service interfaces");

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

    // ----- ADR-0003 enforcement ---------------------------------------------

    private static ArchCondition<JavaClass> reachStockOnlyViaServiceInterfaces() {
        return new ArchCondition<>(
            "only reach com.orbix.engine.modules.stock.service.*Service interfaces "
                + "(no Impl / repository / entity / non-service package)") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                item.getDirectDependenciesFromSelf().forEach(dep -> {
                    JavaClass target = dep.getTargetClass();
                    String name = target.getName();
                    if (!name.startsWith("com.orbix.engine.modules.stock.")) {
                        return; // not a stock reach
                    }
                    // DTOs and enums are always fine — they are part of the published API.
                    if (name.startsWith("com.orbix.engine.modules.stock.domain.dto.")
                            || name.startsWith("com.orbix.engine.modules.stock.domain.enums.")) {
                        return;
                    }
                    if (name.startsWith("com.orbix.engine.modules.stock.service.")) {
                        if (target.getSimpleName().endsWith("Impl")) {
                            events.add(SimpleConditionEvent.violated(item,
                                item.getName() + " depends on stock service implementation " + name
                                    + " — ADR-0003 permits the *Service interface only"));
                        }
                        return;
                    }
                    events.add(SimpleConditionEvent.violated(item,
                        item.getName() + " reaches into stock module class " + name
                            + " — ADR-0003 restricts procurement→stock to *Service interfaces "
                            + "(no entities, repositories, or other internals)"));
                });
            }
        };
    }
}
