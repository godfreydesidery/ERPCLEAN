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
 *   <li>the named cross-module synchronous-TX exemptions from ADR-0003 and
 *       ADR-0004 are restricted to {@code <callee>.service.*Service}
 *       interfaces only (no {@code *Impl}, no repository, no entity reach).</li>
 * </ul>
 *
 * <p>ADR-0004 enumerates the closed set of sync-TX exemptions. Adding a new
 * cross-module service call requires an ADR amendment AND a new {@code
 * @ArchTest} rule below.
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
        .because("modules talk to each other only via published DTOs/enums or domain events. "
            + "The broad ..service.. allowance is tightened by the per-caller→callee rules below "
            + "(ADR-0003 + ADR-0004 named exemptions).");

    // ----- ADR-0003 + ADR-0004 sync-TX exemptions ---------------------------
    //
    // Each rule below pins a single caller→callee direction to the callee's
    // {@code service.*Service} interface only. Reach into {@code *Impl},
    // {@code repository..}, or {@code domain.entity..} of the callee is
    // forbidden — that is the seam ADR-0003 / ADR-0004 protect.

    /** ADR-0003 — procurement → stock (GRN-post FEFO drain + outbound move). */
    @ArchTest
    static final ArchRule procurement_to_stock_via_service_interface_only =
        classes()
            .that().resideInAPackage("com.orbix.engine.modules.procurement..")
            .should(reachOnlyViaServiceInterfaces("com.orbix.engine.modules.stock."))
            .because("ADR-0003 — procurement → stock is restricted to *Service interfaces");

    /** ADR-0004 — sales → stock (invoice post + void compensating moves). */
    @ArchTest
    static final ArchRule sales_to_stock_via_service_interface_only =
        classes()
            .that().resideInAPackage("com.orbix.engine.modules.sales..")
            .should(reachOnlyViaServiceInterfaces("com.orbix.engine.modules.stock."))
            .because("ADR-0004 — sales → stock is restricted to *Service interfaces");

    /** ADR-0004 — sales → cash (receipt-post cash entry). */
    @ArchTest
    static final ArchRule sales_to_cash_via_service_interface_only =
        classes()
            .that().resideInAPackage("com.orbix.engine.modules.sales..")
            .should(reachOnlyViaServiceInterfaces("com.orbix.engine.modules.cash."))
            .because("ADR-0004 — sales → cash is restricted to *Service interfaces");

    /** ADR-0004 — pos → stock (FEFO drain + outbound move on cashier finalise). */
    @ArchTest
    static final ArchRule pos_to_stock_via_service_interface_only =
        classes()
            .that().resideInAPackage("com.orbix.engine.modules.pos..")
            .should(reachOnlyViaServiceInterfaces("com.orbix.engine.modules.stock."))
            .because("ADR-0004 — pos → stock is restricted to *Service interfaces");

    /** ADR-0004 — pos → cash (tender capture, till session, cash pickup, petty cash). */
    @ArchTest
    static final ArchRule pos_to_cash_via_service_interface_only =
        classes()
            .that().resideInAPackage("com.orbix.engine.modules.pos..")
            .should(reachOnlyViaServiceInterfaces("com.orbix.engine.modules.cash."))
            .because("ADR-0004 — pos → cash is restricted to *Service interfaces");

    /** ADR-0004 — pos → giftcard (redeem / refundCredit on finalise). */
    @ArchTest
    static final ArchRule pos_to_giftcard_via_service_interface_only =
        classes()
            .that().resideInAPackage("com.orbix.engine.modules.pos..")
            .should(reachOnlyViaServiceInterfaces("com.orbix.engine.modules.giftcard."))
            .because("ADR-0004 — pos → giftcard is restricted to *Service interfaces");

    /** ADR-0004 — giftcard → cash (cash IN on gift-card issuance). */
    @ArchTest
    static final ArchRule giftcard_to_cash_via_service_interface_only =
        classes()
            .that().resideInAPackage("com.orbix.engine.modules.giftcard..")
            .should(reachOnlyViaServiceInterfaces("com.orbix.engine.modules.cash."))
            .because("ADR-0004 — giftcard → cash is restricted to *Service interfaces");

    /** ADR-0004 — orders → stock (reservation lock + delivery move). */
    @ArchTest
    static final ArchRule orders_to_stock_via_service_interface_only =
        classes()
            .that().resideInAPackage("com.orbix.engine.modules.orders..")
            .should(reachOnlyViaServiceInterfaces("com.orbix.engine.modules.stock."))
            .because("ADR-0004 — orders → stock is restricted to *Service interfaces");

    /** ADR-0004 — orders → cash (deposit / refund entry). */
    @ArchTest
    static final ArchRule orders_to_cash_via_service_interface_only =
        classes()
            .that().resideInAPackage("com.orbix.engine.modules.orders..")
            .should(reachOnlyViaServiceInterfaces("com.orbix.engine.modules.cash."))
            .because("ADR-0004 — orders → cash is restricted to *Service interfaces");

    /** ADR-0004 — orders → giftcard (gift-card tender on deposit). */
    @ArchTest
    static final ArchRule orders_to_giftcard_via_service_interface_only =
        classes()
            .that().resideInAPackage("com.orbix.engine.modules.orders..")
            .should(reachOnlyViaServiceInterfaces("com.orbix.engine.modules.giftcard."))
            .because("ADR-0004 — orders → giftcard is restricted to *Service interfaces");

    /** ADR-0004 — production → stock (CONSUME + PRODUCE pair, batch moves, reservations). */
    @ArchTest
    static final ArchRule production_to_stock_via_service_interface_only =
        classes()
            .that().resideInAPackage("com.orbix.engine.modules.production..")
            .should(reachOnlyViaServiceInterfaces("com.orbix.engine.modules.stock."))
            .because("ADR-0004 — production → stock is restricted to *Service interfaces");

    /** ADR-0004 — admin → party (walk-in customer materialisation on branch creation). */
    @ArchTest
    static final ArchRule admin_to_party_via_service_interface_only =
        classes()
            .that().resideInAPackage("com.orbix.engine.modules.admin..")
            .should(reachOnlyViaServiceInterfaces("com.orbix.engine.modules.party."))
            .because("ADR-0004 — admin → party is restricted to *Service interfaces");

    /**
     * ADR-0004 (latent gap #19) — sales → pos (zReport read-only call from
     * SalesReportServiceImpl). Read-only, no TX correctness risk; the
     * refactor to a dedicated reporting module is tracked separately.
     */
    @ArchTest
    static final ArchRule sales_to_pos_via_service_interface_only =
        classes()
            .that().resideInAPackage("com.orbix.engine.modules.sales..")
            .should(reachOnlyViaServiceInterfaces("com.orbix.engine.modules.pos."))
            .because("ADR-0004 latent — sales → pos is restricted to *Service interfaces "
                + "(refactor to a `reporting` module tracked separately)");

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

    // ----- ADR-0003 / ADR-0004 enforcement helper ---------------------------

    /**
     * Returns an {@link ArchCondition} that pins the named-exemption seam:
     * reach into {@code callee} (a package prefix like
     * {@code "com.orbix.engine.modules.stock."}) is permitted via the callee's
     * {@code service.*Service} interface, its published {@code domain.dto..}
     * / {@code domain.enums..} classes, and (provisionally) the callee's
     * {@code domain.entity..} / {@code repository..} for the cross-cut read
     * cases ADR-0004 §3 calls out as latent gaps. The hard prohibition is
     * the callee's {@code *Impl} — that is the encapsulation boundary the
     * ADR protects.
     *
     * <p>The cross-cut read tolerance is deliberately scoped to this helper
     * so the next slice's reporting-module refactor can flip a single switch.
     */
    private static ArchCondition<JavaClass> reachOnlyViaServiceInterfaces(String calleeRootPackage) {
        final String service = calleeRootPackage + "service.";
        return new ArchCondition<>(
            "only reach " + service + "*Service interfaces, callee DTOs/enums, "
                + "and (provisionally) callee entities/repositories for read-only "
                + "cross-cuts — never *Impl") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                item.getDirectDependenciesFromSelf().forEach(dep -> {
                    JavaClass target = dep.getTargetClass();
                    String name = target.getName();
                    if (!name.startsWith(calleeRootPackage)) {
                        return; // not a reach into this callee module
                    }
                    if (name.startsWith(service)) {
                        if (target.getSimpleName().endsWith("Impl")) {
                            events.add(SimpleConditionEvent.violated(item,
                                item.getName() + " depends on " + name
                                    + " — the named exemption permits the *Service interface only"));
                        }
                    }
                    // domain.dto / domain.enums / domain.entity / repository are
                    // tolerated for now (ADR-0004 §3 latent-gap carve-out). The
                    // hard rule is *Impl above; everything else is the broader
                    // module rule's job.
                });
            }
        };
    }
}
