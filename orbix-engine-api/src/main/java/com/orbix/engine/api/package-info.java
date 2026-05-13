/**
 * REST controllers for the entire backend.
 *
 * Holds:
 *   - controllers for every business module (party, catalog, stock,
 *     procurement, sales, pos, cash, day) — kept here, not inside each
 *     module folder, so the full HTTP surface is auditable in one place
 *   - cross-cutting controllers that do not belong to any business
 *     module (health, system status, error handlers)
 *
 * Layering rule: controllers depend on {@code modules.<m>.service} and
 * {@code modules.<m>.domain.dto}, never on {@code modules.<m>.repository}
 * or {@code modules.<m>.domain.entity} directly. Enforced by ArchUnit.
 */
package com.orbix.engine.api;
