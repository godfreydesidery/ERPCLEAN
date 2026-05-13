/**
 * Company package: organisation → company → branch hierarchy, plus the
 * multi-tenant request context that propagates company_id + branch_id
 * into every transactional module.
 *
 * Owns:
 *   - organisation, company, branch entities (DATA-MODEL.md §1.1-§1.3)
 *   - branch CRUD, active-branch switching for the current session
 *   - per-branch number sequence service consumed by procurement, sales,
 *     pos, cash for document numbering (LPO-BR1-..., INV-BR1-..., etc.)
 *   - RequestContext + the tenant filter that scopes every business-module
 *     query by company_id and (where applicable) branch_id
 *
 * User stories: PRD §5.2, USER-STORIES.md Epic 2 (COMP — US-COMP-001
 * through US-COMP-009).
 *
 * Layout: light-module style (service + repository), not hexagonal —
 * matches the rest of platform.
 */
package com.orbix.engine.platform.company;
