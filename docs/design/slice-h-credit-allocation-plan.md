# Slice H — Customer credit-note allocation (apply credit to open invoices)

| Field | Value |
|---|---|
| Branch | `harden/credit-allocation` |
| Prereqs | V30 customer-returns + credit-notes shipped earlier (DRAFT → POSTED → CREDITED state machine works; credit notes get created with `allocated_amount = 0` and status `POSTED`). |
| Owner | PM coordinating; backend + frontend + qa in parallel |
| Date | 2026-05-28 |
| Target | ~1 day end-to-end |
| Closes | **US-SALES-011** (apply a customer credit note to an invoice) + completes the **US-SALES-010** loop with an e2e gate that exercises the full flow. |

## Background

V30 introduced `customer_return`, `customer_return_line`, `customer_credit_note` and shipped the DRAFT → POSTED → CREDITED return lifecycle. The migration's own comment marks the gap:

> Credit-note allocation to open invoices comes with a later slice. — V30__customer_returns.sql

That later slice is **this one**. Today a credit note exists as a POSTED `customer_credit_note` row with `allocated_amount = 0` and there is no way to apply it. The slice fills the gap so a salesperson can settle outstanding invoices using available credit.

## 1. Scope

**In:**

- **One new entity + table** — `customer_credit_note_allocation` (V75). Mirror of `receipt_allocation` shape: one row per (credit-note, invoice) application, tracks `amount`, `allocated_at`, `allocated_by`. Many allocations per credit note (partial applies), many allocations per invoice (multiple credit notes against one invoice).
- **One new endpoint** — `POST /api/v1/sales/customer-credit-notes/uid/{uid}/apply`. Body: `{ salesInvoiceUid, amount }`. Returns the updated `CustomerCreditNoteDto` with refreshed `allocatedAmount` + `status`.
- **One new outbox event** — `CustomerCreditNoteApplied.v1`. Payload: `{ creditNoteUid, salesInvoiceUid, amount, currencyCode, allocatedByUserId }`. Downstream subscribers: AR aging refresh (already pulls from `sales_invoice.paidAmount`, so no schema-side subscription needed); accounting export when it lands in Phase 3.
- **One new state on `CreditNoteStatus`** — add `PARTIALLY_ALLOCATED` between `POSTED` and `FULLY_ALLOCATED`. State machine: `POSTED` → `PARTIALLY_ALLOCATED` (on first partial apply) → `FULLY_ALLOCATED` (when `allocatedAmount == totalAmount`).
- **Same-tx update** of `sales_invoice.paidAmount` via the existing `SalesInvoiceService.applyCreditNote(invoiceId, amount)` method — add it if absent, mirroring the `applyWriteOff` method added in Slice G.2. Increment `paidAmount`; if it equals `totalAmount`, move invoice status from `PARTIALLY_PAID` (or `POSTED`) to `PAID`.
- **Permission** — reuse existing `SALES.MANAGE_RETURN` (id 34). The salesperson who creates a return + issues a credit note also applies it; no perm split.
- **Web UI** — extend `features/sales/returns.component.ts`:
  - Credit-notes table gets a new "Apply" action button per row (visible while `status != FULLY_ALLOCATED`).
  - "Apply" opens a modal that lists the customer's open invoices (POSTED + PARTIALLY_PAID with `paidAmount < totalAmount`) and lets the operator pick one + amount (defaults to `min(creditAvailable, invoiceOutstanding)`).
  - On submit, calls the new endpoint and refreshes the credit-note row.
  - Loading / empty / error / populated states on the modal.
- **E2E spec** — new `e2e/customer-returns.spec.ts`. Two test groups:
  - Happy path: salesperson creates a return for an existing invoice → posts → issues credit note → applies credit note to a different open invoice → invoice's `paidAmount` reflects the apply. Includes axe-core on each new screen.
  - Permission gate: a persona without `SALES.MANAGE_RETURN` (`sales-clerk` if mismatched, otherwise the cashier) → 403 on the apply endpoint.
- **Hardening sweep** — JSON pin test on `CustomerCreditNoteDto` confirming `allocatedAmount` is numeric, status enum serialises as string, Long ids stringify.

**Out:**

- **Cash refund of a credit note** (i.e. paying out the customer in cash instead of applying to an invoice) — separate slice. The current scope only handles invoice application.
- **Auto-apply** when the credit note is issued — the salesperson always picks an invoice explicitly. Auto-apply against the oldest overdue invoice is a UX nicety deferred to a follow-up.
- **Apply across multiple invoices in one call** (the body takes a single `salesInvoiceUid`). Repeat applies if needed.
- **Reverse / un-apply an allocation** — separate slice with its own dual-approval / supervisor gate. Mistakes during this slice are handled by issuing a new credit note.
- **Supplier-side equivalent** (apply vendor credit note to supplier invoice — US-PROC-009) — Slice H.1 mirror; out of scope here.

## 2. Permission band

No new perms. Reuses `SALES.MANAGE_RETURN` (id 34, V28). The same operator who posts the return + issues the credit note also applies it — splitting the action would be modelling theatre. If a future user-org wants the split, add `SALES.CREDIT_NOTE.APPLY` (next free band 136) in a 1-migration refactor.

## 3. Schema — `V75__customer_credit_note_allocation.sql`

```sql
-- Slice H — credit-note → invoice allocations. Mirror of receipt_allocation.
-- Many allocations per credit note (partial applies); many allocations per
-- invoice (multiple credits against one invoice).
CREATE TABLE customer_credit_note_allocation (
    id                       BIGINT         NOT NULL,
    customer_credit_note_id  BIGINT         NOT NULL,
    sales_invoice_id         BIGINT         NOT NULL,
    amount                   DECIMAL(18, 4) NOT NULL,
    allocated_at             TIMESTAMP      NOT NULL,
    allocated_by             BIGINT         NOT NULL,
    CONSTRAINT pk_customer_credit_note_allocation  PRIMARY KEY (id),
    CONSTRAINT fk_ccna_credit_note  FOREIGN KEY (customer_credit_note_id) REFERENCES customer_credit_note (id),
    CONSTRAINT fk_ccna_invoice      FOREIGN KEY (sales_invoice_id)        REFERENCES sales_invoice        (id)
);

CREATE INDEX ix_ccna_credit_note ON customer_credit_note_allocation (customer_credit_note_id);
CREATE INDEX ix_ccna_invoice     ON customer_credit_note_allocation (sales_invoice_id);

CREATE SEQUENCE customer_credit_note_allocation_seq START WITH 1000 INCREMENT BY 50;
```

Pre-stable schema; if there's a column-type mismatch on first boot, edit V75 in place + recreate volume (per [[feedback-ephemeral-migrations]]).

## 4. Endpoint — sketch

```java
@PostMapping("/customer-credit-notes/uid/{uid}/apply")
@PreAuthorize("hasAuthority('SALES.MANAGE_RETURN')")
public CustomerCreditNoteDto apply(
        @PathVariable @ValidUlid String uid,
        @RequestBody @Valid ApplyCreditNoteRequestDto request) {
    return service.applyToInvoice(uid, request);
}
```

Lives on the existing `CustomerReturnController` (it already handles the credit-note GET; keep credit-note CRUD on the same controller for cohesion).

## 5. DTOs

```java
public record ApplyCreditNoteRequestDto(
    @NotBlank @ValidUlid String salesInvoiceUid,
    @NotNull @DecimalMin("0.01") BigDecimal amount
) {}
```

`CustomerCreditNoteDto` already exists; verify it exposes `allocatedAmount`, `availableAmount = totalAmount - allocatedAmount`, and a list of allocations (add the list as a nested record `Allocation` if absent — invoice number, amount, allocated_at, allocated_by-username).

## 6. Service — `CustomerReturnServiceImpl#applyToInvoice`

```java
@Transactional
public CustomerCreditNoteDto applyToInvoice(String creditNoteUid, ApplyCreditNoteRequestDto req) {
  // 1. Load credit note via uid; validate same company + status in {POSTED, PARTIALLY_ALLOCATED}.
  // 2. Validate amount > 0 AND amount ≤ (totalAmount - allocatedAmount).
  // 3. Load target sales_invoice via salesInvoiceUid; validate same company + same customer
  //    AND status in {POSTED, PARTIALLY_PAID} AND (totalAmount - paidAmount) >= amount.
  // 4. Persist customer_credit_note_allocation row.
  // 5. credit_note.allocated_amount += amount; if equal to totalAmount set status=FULLY_ALLOCATED,
  //    else status=PARTIALLY_ALLOCATED.
  // 6. salesInvoiceService.applyCreditNote(invoice.id, amount) — increments paidAmount,
  //    flips invoice status to PAID if fully paid.
  // 7. Emit CustomerCreditNoteApplied.v1 via outbox.
  // 8. Return hydrated DTO.
}
```

If `SalesInvoiceService.applyCreditNote` does not yet exist, add it mirroring `applyWriteOff` from Slice G.2.

## 7. ADR-0004 — exemption inventory

Adds one line (in the same style as the G.2 entry):

> `customer_credit_note_allocation` write + `customer_credit_note.allocated_amount` update + `sales_invoice.paidAmount` update happen in the same DB tx as the outbox `CustomerCreditNoteApplied.v1` event. Justification: the invariant "credit-note `allocated_amount` matches the sum of allocations AND `sales_invoice.paidAmount` reflects the applied credit" must hold strictly; eventual consistency would break AR aging during the gap.

No ADR-0005 amendment.

## 8. Frontend touchpoints

| File | Change |
|---|---|
| `features/sales/returns.component.ts` (+ .html / .scss / .spec) | Add "Apply" action on credit-note rows. Open modal. |
| NEW `features/sales/credit-note-apply-modal.component.ts` (+ spec) | Pick invoice (from open-invoices list) + amount + submit. 4 states. |
| `features/sales/sales.service.ts` (or `returns.service.ts`) | Add `applyCreditNote(uid, request)` method; add `getOpenInvoicesForCustomer(customerUid)` helper if not already present. |
| `features/sales/sales.models.ts` | Add `ApplyCreditNoteRequest` type; extend `CustomerCreditNote` if needed with `availableAmount` + `allocations` array. |

## 9. Task list — parallel fan-out

| # | Owner | Deliverable | Acceptance |
|---|---|---|---|
| 1 | **qa-engineer** | NEW `e2e/customer-returns.spec.ts` — ~6 scenarios as `test.fail` until backend lands: happy-path return → credit → apply → invoice paid; partial apply twice → FULLY_ALLOCATED; over-apply → 422; cross-customer apply → 422; permission 403 for `sales-clerk` / `cashier`; axe-core on the apply modal. | Spec compiles, runs `test.fail`. |
| 2 | **backend-engineer** | V75 migration; `CustomerCreditNoteAllocation` entity; `ApplyCreditNoteRequestDto`; `customer-credit-notes/uid/{uid}/apply` endpoint; `applyToInvoice` service method + tests; add `PARTIALLY_ALLOCATED` to `CreditNoteStatus`; add `SalesInvoiceService.applyCreditNote` if absent; `CustomerCreditNoteApplied.v1` event payload; ADR-0004 inventory append; JSON pin test on the response DTO with `allocations` array. | `mvn test` 622+/0fail (HealthSmokeTest's known 1 error). |
| 3 | **frontend-engineer** | Returns component "Apply" action; new `credit-note-apply-modal.component.ts`; service + models extensions; unit specs. | `npm test` green; `npm run build` green. |
| 4 | **integration** | Cherry-pick, validate, QA-image rebuild, smoke. | All green. |

## 10. Open questions — none requiring user input

- Single-invoice apply per call vs multi-invoice in one POST — picked single. Rationale: clean error semantics (one failure rolls back one apply); UX can iterate.
- Self-credit-note creation (issue without a return) — out of scope. Today every credit note is return-driven; standalone credit-notes are a separate slice.

---

**Total estimate**: ~1 day end-to-end. Smaller than G.2 because no new perms, no dual-approval, single state-machine transition, one new endpoint.
