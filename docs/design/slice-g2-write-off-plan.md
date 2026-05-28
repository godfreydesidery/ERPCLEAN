# Slice G.2 — Debt write-off (AR + AP, dual approval)

| Field | Value |
|---|---|
| Branch | `harden/debt-write-off` |
| Prereqs | Slice G + G.1 merged. `DEBT.*` band 130-133 seeded. `accountant` persona holds the full band. |
| Owner | PM coordinating; backend + frontend + qa in parallel |
| Date | 2026-05-28 |
| Target | ~1.5 days end-to-end |
| Closes | **US-DEBT-004** (write off a debt with dual approval — AR + AP combined surface) |

## 1. Scope

**In:**

- **Two new permissions** (band 134, 135):
  - `DEBT.WRITE_OFF.REQUEST` (134) — submit a write-off request
  - `DEBT.WRITE_OFF.APPROVE` (135) — approve / reject a pending request
- **One new entity + table** — `debt_write_off` (V73):
  - Single table covers **both** AR (sales-invoice) and AP (supplier-invoice) write-offs via a `targetKind` discriminator (`CUSTOMER_INVOICE` / `SUPPLIER_INVOICE`). No separate tables — same operator (accountant) does both.
- **One config knob** — `orbix.debt.write-off.dual-approval-threshold` (default **TZS 100,000**). Above the threshold, requester ≠ approver is enforced. **At or below**, requester can self-approve in one POST (same DB tx — sets status straight to `POSTED`) provided they also hold `DEBT.WRITE_OFF.APPROVE`.
- **State machine** — `PENDING_APPROVAL` → `POSTED` (approved) or `REJECTED`. Three statuses total. No separate APPROVED state (approval and post happen in one tx because there's nothing else to do between them).
- **Five REST endpoints** under `/api/v1/debt/write-offs` (new sibling controller `DebtWriteOffController`):
  - `POST /api/v1/debt/write-offs` — create + submit. Body: `{ targetKind, targetInvoiceUid, amount, reason }`. Returns `DebtWriteOffDto`. If amount ≤ threshold AND caller has `APPROVE` perm → auto-posts (status `POSTED`, sets `approvedByUserId = requesterId`, emits `Posted` event). Otherwise → status `PENDING_APPROVAL`, emits `Requested` event.
  - `POST /api/v1/debt/write-offs/uid/{uid}/approve` — gated `DEBT.WRITE_OFF.APPROVE`. Enforces `approverUserId != requesterUserId` when amount > threshold. Transitions `PENDING_APPROVAL` → `POSTED`. Emits `Posted` event.
  - `POST /api/v1/debt/write-offs/uid/{uid}/reject` — gated `DEBT.WRITE_OFF.APPROVE`. Body: `{ reasonForReject }`. Same different-user rule. Transitions `PENDING_APPROVAL` → `REJECTED`. Emits `Rejected` event.
  - `GET /api/v1/debt/write-offs?status=&kind=&page=&size=` — paged list, default sort = `requestedAt desc`. Gated `DEBT.READ`.
  - `GET /api/v1/debt/write-offs/uid/{uid}` — single. Gated `DEBT.READ`.
- **Three outbox events** — `DebtWriteOffRequested.v1`, `DebtWriteOffPosted.v1`, `DebtWriteOffRejected.v1`. Payloads carry `targetKind`, `targetInvoiceId`, `amount`, `requesterUserId`, `approverUserId` (null for Requested + Rejected), `reason`, `reasonForReject` (null for first two).
- **Effect on the target invoice** — when status moves to `POSTED`, set `paidAmount = totalAmount` on the target invoice in the **same tx**. Aging queries (`findAllOpenForAging` AR + AP) already exclude `paidAmount >= totalAmount` rows, so written-off invoices fall out of the dunning queues automatically. **No new invoice status** introduced; the write-off table is the source of truth for "was this written off vs. actually paid".
- **Reverse linkage** — `sales_invoice.paidAmount` and `supplier_invoice.paidAmount` set via service-layer call to the existing invoice services (`SalesInvoiceServiceImpl` + `SupplierInvoiceServiceImpl`) — keep the write logic in the invoice owner module, not in the write-off service.
- **Permission widening** — `accountant` persona gets BOTH `DEBT.WRITE_OFF.REQUEST` and `DEBT.WRITE_OFF.APPROVE`. The dual-approval gate is enforced by `requesterUserId != approverUserId` at the service layer, not by perm split. (Tanzania-first finance teams are small; same role holds both perms but two humans are still needed for amounts above the threshold.)
- **New QA persona** — `qa.accountant.approver` (mirror of `qa.accountant` with the same perms) so the spec can exercise the dual-approval path with two distinct users.
- **UI**:
  - **`/debt/write-offs`** new queue page (sibling of `/debt`) — table of all write-off requests with status filter chips (`PENDING_APPROVAL` / `POSTED` / `REJECTED`) and kind filter (`AR` / `AP`). Click a row → detail drawer with full audit trail (requester, approver, timestamps, reason, reasonForReject). Approve / Reject buttons on `PENDING_APPROVAL` rows for users with `APPROVE` perm.
  - **"Write off" button** on the customer drill-down (`/debt/customer/uid/:uid`) and supplier drill-down (`/debt/supplier/uid/:uid`) open-invoices table — opens a modal with `amount` (defaults to `totalAmount - paidAmount`), `reason` textarea, and a submit button. Posts to `/api/v1/debt/write-offs` and shows the resulting status (auto-posted or pending).
  - **Sidebar nav** — add `/debt/write-offs` link under the Debt section.

**Out:**

- **Reversal of a posted write-off** — separate slice (G.3) with its own perm `DEBT.WRITE_OFF.REVERSE` and own dual-approval. Mistakes get a new write-off-correction entry, not a reversal in G.2.
- **Partial write-off across multiple invoices in one request** — single-invoice only in G.2. Bulk write-off is a separate UX shape (CSV import or multi-select queue) deferred.
- **Customer / supplier-level write-off** (not tied to a specific invoice) — out of scope. All write-offs are invoice-scoped in G.2.
- **Approval threshold per amount band** (e.g. < TZS 50K self-approve, 50K-500K supervisor, > 500K finance director) — single threshold only. Bands can be added later by extending the service guard.
- **Email / SMS notification when a request lands in the queue** — no notification infra; out of scope.
- **A `debt_entry` compensating ledger row** (mentioned in US-DEBT-004) — ADR-0005 explicitly rejects the `debt_entry` ledger. The write-off table itself + the invoice paidAmount update is the audit trail. ADR-0005 stays as-is; US-DEBT-004 AC line "Posts a compensating `debt_entry` and logs the reason" is **reinterpreted** as "logs the reason" only (write-off record is the compensating row).

## 2. Permission band

Current high-water: **133** (`DEBT.CREDIT_LIMIT.UPDATE`, V70 / Slice G). G.2 consumes **2 new ids**:

| Id | Constant | Granted to (V74 seed) |
|---|---|---|
| 134 | `DEBT.WRITE_OFF.REQUEST` | `accountant` role |
| 135 | `DEBT.WRITE_OFF.APPROVE` | `accountant` role |

Both perms on the same role for now (Tanzania-first small teams). Dual-approval is enforced by user-id distinction at the service layer. Future role split (`finance-officer` keeps REQUEST, `finance-manager` keeps APPROVE) is a one-migration refactor when the user-org demands it — documented as a follow-up only.

Band 136+ stays reserved for slice fix-ups.

## 3. Schema — `V73__debt_write_off.sql`

```sql
-- Slice G.2 — debt write-off requests. Single table covers AR + AP via
-- targetKind discriminator. PENDING_APPROVAL → POSTED or REJECTED.
CREATE TABLE debt_write_off (
    id BIGINT NOT NULL,
    uid CHAR(26) NOT NULL,
    company_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    target_kind VARCHAR(32) NOT NULL,           -- CUSTOMER_INVOICE | SUPPLIER_INVOICE
    target_invoice_id BIGINT NOT NULL,
    target_invoice_uid CHAR(26) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    status VARCHAR(32) NOT NULL,                 -- PENDING_APPROVAL | POSTED | REJECTED
    requested_by_user_id BIGINT NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    approved_by_user_id BIGINT,
    approved_at TIMESTAMP,
    posted_at TIMESTAMP,
    rejected_at TIMESTAMP,
    reason_for_reject VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_debt_write_off PRIMARY KEY (id),
    CONSTRAINT uk_debt_write_off_uid UNIQUE (uid)
);

CREATE INDEX ix_debt_write_off_company_status
    ON debt_write_off (company_id, status);
CREATE INDEX ix_debt_write_off_branch_requested
    ON debt_write_off (company_id, branch_id, requested_at DESC);
CREATE INDEX ix_debt_write_off_target
    ON debt_write_off (target_kind, target_invoice_id);

CREATE SEQUENCE debt_write_off_seq START WITH 1000 INCREMENT BY 50;
```

`V74__seed_debt_write_off_permissions.sql` seeds perm ids 134 + 135 and grants both to `accountant` role.

## 4. Endpoints — sketch

```java
@RestController
@RequestMapping("/api/v1/debt/write-offs")
@PreAuthorize("hasAuthority('DEBT.READ')")
public class DebtWriteOffController {

    @PostMapping
    @PreAuthorize("hasAuthority('DEBT.WRITE_OFF.REQUEST')")
    public DebtWriteOffDto create(@RequestBody @Valid CreateDebtWriteOffRequestDto req) { ... }

    @PostMapping("/uid/{uid}/approve")
    @PreAuthorize("hasAuthority('DEBT.WRITE_OFF.APPROVE')")
    public DebtWriteOffDto approve(@PathVariable @ValidUlid String uid) { ... }

    @PostMapping("/uid/{uid}/reject")
    @PreAuthorize("hasAuthority('DEBT.WRITE_OFF.APPROVE')")
    public DebtWriteOffDto reject(@PathVariable @ValidUlid String uid,
                                  @RequestBody @Valid RejectDebtWriteOffRequestDto req) { ... }

    @GetMapping
    public PageDto<DebtWriteOffDto> list(@RequestParam(required=false) DebtWriteOffStatus status,
                                         @RequestParam(required=false) DebtWriteOffTargetKind kind,
                                         @RequestParam(defaultValue="0") int page,
                                         @RequestParam(defaultValue="25") int size) { ... }

    @GetMapping("/uid/{uid}")
    public DebtWriteOffDto get(@PathVariable @ValidUlid String uid) { ... }
}
```

## 5. DTO shapes

```java
public record DebtWriteOffDto(
    Long id,
    String uid,
    DebtWriteOffTargetKind targetKind,
    Long targetInvoiceId,
    String targetInvoiceUid,
    String targetInvoiceNumber,      // hydrated for UI
    String partyName,                // customer or supplier name, hydrated
    BigDecimal amount,
    String currencyCode,
    String reason,
    DebtWriteOffStatus status,
    Long requestedByUserId,
    String requestedByUsername,
    Instant requestedAt,
    Long approvedByUserId,
    String approvedByUsername,
    Instant approvedAt,
    Instant postedAt,
    Instant rejectedAt,
    String reasonForReject
) {}

public record CreateDebtWriteOffRequestDto(
    @NotNull DebtWriteOffTargetKind targetKind,
    @NotBlank @ValidUlid String targetInvoiceUid,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank @Size(max=2000) String reason
) {}

public record RejectDebtWriteOffRequestDto(
    @NotBlank @Size(max=2000) String reasonForReject
) {}
```

## 6. Service — `DebtWriteOffServiceImpl`

Module: `modules/debt/` if a new module is justified, otherwise `modules/sales/service/` for now (AR-leaning) — **recommendation: keep it in `modules/sales/service/`** because the AR side dominates and adding a new module just for write-offs creates a third "debt" location (we already have `modules/sales` for AR + `modules/procurement` for AP). The cross-module dependency on `SupplierInvoiceServiceImpl` is exactly the kind of bounded call ADR-0004 catalogues.

Methods:
- `create(CreateDebtWriteOffRequestDto, RequestContext)` — validates target invoice exists in tenant, amount ≤ outstanding, computes auto-post path, persists, emits event, optionally calls invoice service to update paidAmount (if auto-posted).
- `approve(String uid)` — loads, validates state + user-id distinction + amount-vs-threshold, transitions to POSTED, calls invoice service, emits event.
- `reject(String uid, RejectDebtWriteOffRequestDto)` — loads, validates state + user-id distinction, transitions to REJECTED, emits event.
- `list(...)`, `get(uid)` — straightforward reads.

Tenant guard via `RequestContext` everywhere (companyId from token, not request).

## 7. Persona impact

- **`accountant`** — widen with `DEBT.WRITE_OFF.REQUEST` + `DEBT.WRITE_OFF.APPROVE`.
- **NEW persona `qa.accountant.approver`** — second accountant account in `test-users.ts` with the same role grants, so the spec can run a two-user dual-approval flow.
- **`sales-clerk`** — 403 on all write-off endpoints.
- **`procurement-officer`** — 403 on all write-off endpoints (consistent with G.1).

## 8. Frontend — file changes

- NEW `features/debt/debt-write-offs.component.ts` (+ template + spec) — queue page + detail drawer + approve/reject actions.
- NEW `features/debt/debt-write-off-modal.component.ts` (+ template + spec) — write-off creation modal, used on customer + supplier drill-downs.
- MOD `features/debt/debt-customer.component.ts` — add "Write off" button on each open-invoice row, opens the modal.
- MOD `features/debt/debt-supplier.component.ts` — same.
- MOD `features/debt/debt.service.ts` — add `createWriteOff`, `listWriteOffs`, `getWriteOff`, `approveWriteOff`, `rejectWriteOff` methods.
- MOD `features/debt/debt.models.ts` — add `DebtWriteOff`, `DebtWriteOffStatus`, `DebtWriteOffTargetKind`, `CreateDebtWriteOffRequest`, `RejectDebtWriteOffRequest` types. Long-id fields typed as `string`.
- MOD `features/debt/debt.routes.ts` — add `/debt/write-offs` route.
- MOD `app.routes.ts` or sidebar component — add nav link.

## 9. Task list — parallel fan-out

| # | Owner | Deliverable | Acceptance |
|---|---|---|---|
| 1 | **qa-engineer** | Extend `e2e/debt.spec.ts` with ~7 G.2 scenarios (expected-fail until backend lands): create-and-auto-post (≤ threshold), create-pending (> threshold), approve-by-different-user → POSTED, approve-by-requester-when-above-threshold → 409, reject, sales-clerk 403, procurement-officer 403. Add the new `qa.accountant.approver` persona to `test-users.ts`. | Spec compiles, runs `test.fail`. |
| 2 | **backend-engineer** | Full backend G.2 per §3-§7. V73 + V74 migrations, entity + DTOs + service + controller + outbox-event types + tests (repo, service impl, JSON pin on `DebtWriteOffDto`). Wire-up to `SalesInvoiceService` and `SupplierInvoiceService` for paidAmount update on POSTED. | `mvn test` 596+ run, no new fails. |
| 3 | **frontend-engineer** | Full frontend per §8. Queue page, write-off modal, drill-down buttons, service+models+routes, unit tests. | `npm test` green, `npm run build` green. |
| 4 | **integration** | Cherry-pick all three onto `harden/debt-write-off`, run mvn + npm tests, build QA image, smoke against local container. | All green. |

## 10. Outbox events

| Event | Emitted at | Payload |
|---|---|---|
| `DebtWriteOffRequested.v1` | `create()` when status starts as PENDING_APPROVAL | `{ uid, targetKind, targetInvoiceId, amount, requesterUserId, reason }` |
| `DebtWriteOffPosted.v1` | `create()` auto-post OR `approve()` transition | `{ uid, targetKind, targetInvoiceId, amount, requesterUserId, approverUserId, reason }` |
| `DebtWriteOffRejected.v1` | `reject()` transition | `{ uid, targetKind, targetInvoiceId, amount, requesterUserId, approverUserId, reasonForReject }` |

Note ADR-0004 inventory: writing to `debt_write_off` + updating `sales_invoice.paidAmount` (or `supplier_invoice.paidAmount`) happens in the same DB tx as the outbox row. Synchronous tx is justified — the same-tx integrity between "write-off recorded" and "invoice marked paid" is essential. Add a line to ADR-0004 listing this exemption.

## 11. ADR amendment

**Yes — ADR-0004 gets a one-line addition** to the sync-tx exemption inventory:

> `debt_write_off` write + `sales_invoice` / `supplier_invoice` paidAmount update happen in the same DB tx as the outbox `DebtWriteOffPosted.v1` event. Justification: the invariant "if a write-off is POSTED, the invoice's paidAmount reflects it" must hold strictly; eventual consistency would break aging queries during the gap.

**No ADR-0005 amendment** — G.2 sits squarely within the `DEBT.*` namespace + write-off table is consistent with ADR-0005's "no debt_entry ledger" stance (the write-off table IS the compensating-row table, just not under that name).

## 12. Open questions — none requiring user input

- Single threshold knob (one number) vs banded thresholds — single in G.2, banded deferred.
- Self-approval at-or-below-threshold — yes (default UX); the spec exercises it.
- Reversal — out of scope, separate slice.

---

**Total estimate**: ~1.5 days end-to-end (similar shape to G.1 — slightly larger because of the new entity + state machine).
