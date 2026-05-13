# Admin module

## 1. Purpose

`admin` owns the **operational master data** that the rest of the system references but doesn't manage: the org → company → branch → section hierarchy, the currency registry, and the FX-rate snapshots. It is the home of the first-run setup wizard and the back-office screens used by HQ admins to add a new branch, spin up a section, or quote today's USD rate.

The other 13 modules read `branch`, `section`, `currency`, `fx_rate` constantly via the `RequestContext` and the `cash` / `pos` ledgers. `admin` is the single writer.

## 2. Scope

In scope:
- `organisation`, `company`, `branch` CRUD (already in V1 DDL — admin owns the Java side).
- `section` CRUD per branch.
- `currency` registry and `fx_rate` daily snapshots.
- First-run setup wizard endpoint (US-COMP-001) — creates org + first company + first branch + walk-in customer per branch in one flow.
- `till_currency` join table — which foreign currencies a till is allowed to accept.
- Active-branch context switching for the current session (US-COMP-005).

Out of scope (referenced but owned elsewhere):
- `app_user`, `role`, `permission`, `user_role` — owned by `auth`.
- `vat_group`, `uom`, `price_list` — owned by `catalog`.
- `number_sequence` administration — owned by `common` (its row management) but configured via admin screens.
- Customer / supplier / employee parties — owned by `party`.

## 3. Domain model

| Entity | Purpose | Key relationships |
|---|---|---|
| `organisation` | Top of the org hierarchy. One per deployment. | — |
| `company` | Legal entity. N per organisation. | `organisation_id` |
| `branch` | Physical site. N per company. | `company_id` |
| `section` | Department within a branch (bakery, butchery, retail floor, etc.). REQUIRED on `pos_sale`, `till`, `bom`, `production_batch`. | `branch_id`, `manager_user_id` |
| `currency` | ISO 4217 code + minor-unit digits. PK is the 3-letter code. | — |
| `fx_rate` | Daily rate snapshot, most-recent ≤ sale time wins. | `from_currency`, `to_currency` |
| `till_currency` | Composite-PK join: which foreign currencies a till accepts. | `till_id`, `currency_code` |

See [DATA-MODEL.md §1.1-1.3, §1.17-1.19](../../../../../../../../DATA-MODEL.md) (the Phase 1.1 additions land here).

## 4. Key business flows

1. **First-run wizard (US-COMP-001).** Single endpoint creates `organisation` + first `company` + first `branch` + default `section` (Retail Floor) + per-branch walk-in customer (via `party`) + default `RETAIL` price list (via `catalog`) + functional-currency seed in `currency`. Emits `OrganisationCreated.v1`, `CompanyCreated.v1`, `BranchCreated.v1`, `SectionCreated.v1`.
2. **Add a branch (US-COMP-004).** HQ admin creates a new `branch`, assigns timezone, default sections auto-created (Retail Floor only; admin adds Bakery / Butchery / etc. on demand). Provisions per-branch number sequences for procurement, sales, POS, orders, gift cards.
3. **Create a section.** Manager-tier user adds a section to their branch: code, name, type, manager. Tills and BOMs can then be assigned to it.
4. **Switch active branch context (US-COMP-005).** Cashier/manager working across branches sets `X-Branch-Id` header (or session setting). JWT carries default branch; header overrides. Backend validates the user has a `user_role` granting them access to that branch.
5. **Manage currencies.** Admin enables a new ISO currency (e.g. add `USD` alongside `UGX`). Tills opt-in via `till_currency`.
6. **Quote an FX rate.** Treasurer or scheduled job posts the day's rate (`UGX` → `USD`, etc.). Most-recent rate ≤ sale time is used by POS at tender.

## 5. Module interactions

**Depends on:**
- `auth` — `app_user` for `manager_user_id`, supervisor PIN flow for branch/section creation.
- `common` — outbox, audit, request context (every admin write is audited).

**Publishes events:**
- `OrganisationCreated.v1`, `CompanyCreated.v1`, `BranchCreated.v1`, `BranchUpdated.v1`, `BranchDeactivated.v1`
- `SectionCreated.v1`, `SectionUpdated.v1`, `SectionDeactivated.v1`
- `CurrencyEnabled.v1`, `FxRateQuoted.v1`
- `TillCurrencyAdded.v1`, `TillCurrencyRemoved.v1`

**Consumes events:**
- None typical. Admin is a source module.

## 6. API surface

Base `/api/v1`.

| Resource | Endpoints |
|---|---|
| `/api/v1/setup` | `POST /first-run` — single-call bootstrap |
| `/api/v1/organisations` | `GET`, `PATCH /{id}` |
| `/api/v1/companies` | `GET`, `POST`, `GET/PATCH /{id}` |
| `/api/v1/branches` | `GET`, `POST`, `GET/PATCH /{id}`, `POST /{id}/deactivate` |
| `/api/v1/branches/{id}/sections` | `GET`, `POST` |
| `/api/v1/sections/{id}` | `GET`, `PATCH`, `POST /deactivate` |
| `/api/v1/currencies` | `GET`, `POST` (enable), `PATCH /{code}` |
| `/api/v1/fx-rates` | `GET` (filter by date / pair), `POST` (quote) |
| `/api/v1/tills/{tillId}/currencies` | `GET`, `PUT` (replace set), `DELETE /{currencyCode}` |
| `/api/v1/session/active-branch` | `PUT` (switch active branch) |

## 7. Persistence

Flyway:
- Existing baseline `V1__platform_baseline.sql` covers `organisation`, `company`, `branch`.
- Phase 1.1 adds `V3__admin_section_currency_fx.sql` (section, currency, fx_rate, till_currency).
- Sequences: `section_seq`, `fx_rate_seq` added in `V1_3__phase11_sequences.sql` (per-dialect).

DB-agnostic types; status enums are `VARCHAR(32)`.

## 8. User stories

**P1 (MVP):**
- US-COMP-001 — Set up the organisation and first company
- US-COMP-002 — Edit company profile
- US-COMP-004 — Create and edit branches
- US-COMP-005 — Set the active branch context
- US-COMP-009 — Create additional companies under the organisation
- US-ADMIN-001 — Create a section within a branch
- US-ADMIN-002 — Assign a section manager
- US-ADMIN-003 — Enable a currency for the company
- US-ADMIN-004 — Quote an FX rate

**P2:**
- US-ADMIN-005 — Deactivate a section (with no open tills / BOMs)
- US-ADMIN-006 — Configure accepted currencies per till
- US-ADMIN-007 — Bulk-import sections from CSV (chains adding new branches at scale)

## 9. Open questions

- **Section type taxonomy** — fixed enum (bakery/butchery/…) or free-form? Currently a fixed enum; consider opening to a custom-codes table if regions vary.
- **FX rate source** — manual quote only, or pull from a feed (central bank API)? MVP is manual.
- **Cross-company branch sharing** — can a branch belong to multiple companies under one organisation? Architecture says no; confirm.

## 10. Implementation notes

- Hexagonal-style layered shape (matches every other module): `domain/{entity,dto,enums,event}/`, `service/`, `repository/`.
- **Invariants:**
  - `section.code` unique per `branch_id`.
  - At least one `RETAIL_FLOOR` section per active branch (enforced at branch-create time and on section-deactivate).
  - `branch.code` unique per `company_id`.
  - `fx_rate.rate > 0`; same-currency rate not allowed.
  - `till_currency` always includes the company's functional currency (auto-added when a till is created).
- **Multi-tenancy:** `RequestContext.companyId` filters all reads. Branch-scoped reads filter by `branch_id` when set on the request.
- **Audit:** every state-changing write goes through `@Auditable` — branch / section creates are high-impact.
- **Idempotency:** all `POST` endpoints accept `Idempotency-Key`; the first-run wizard is keyed by company code to prevent double-bootstrap.
- **Outbox:** every event listed in §5 is emitted in the same transaction as the write.
- **Defaults seeded on company create:**
  - Functional `currency` (from company profile).
  - One `section` of type `RETAIL_FLOOR` named "Main Floor".
  - One walk-in `customer` per branch via the `party` module.
  - Per-branch `number_sequence` rows for LPO / GRN / INV / RCT / TILL / ORD / GC.
