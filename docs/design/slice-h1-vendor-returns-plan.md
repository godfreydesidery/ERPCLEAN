# Slice H.1 — Vendor returns + credit-note allocation (AP mirror of Slice H)

| Field | Value |
|---|---|
| Branch | `harden/vendor-returns` |
| Prereqs | Slice H merged. `SupplierInvoiceService.applyWriteOff` already exists (G.2 reuses it). |
| Owner | PM coordinating; backend + frontend + qa in parallel |
| Date | 2026-05-28 |
| Target | ~1.5–2 days end-to-end |
| Closes | **US-PROC-008** (return goods to a vendor — `vendor_return`) + **US-PROC-009** (receive + allocate a vendor credit note — `vendor_credit_note`). Both P2. |

## Background

The AR side now has the full return → credit-note → apply loop (Slice C + V30 + Slice H). The AP side has **nothing** — no `vendor_return` entity, no `vendor_credit_note`, no apply mechanism. Slice H.1 lands the **whole** AP mirror in one slice (unlike the AR side which was split across V30 + Slice H — we already learned the pattern, no need to split it again).

## 1. Scope

**In:**

- **Four new entities + tables (V76)** — full mirror of customer-returns / credit-note shape from V30 + V75:
  - `vendor_return` (id, uid, number, company_id, branch_id, supplier_id, original_grn_id?, original_supplier_invoice_id?, return_date, reason, total_amount, status, restock, posted_at, posted_by, notes, version, audit fields). State machine `DRAFT → POSTED → CREDITED`.
  - `vendor_return_line` (id, vendor_return_id, line_no, item_id, uom_id, returned_qty, unit_price, vat_group_id, tax_amount, line_total, original_line_id?).
  - `vendor_credit_note` (id, uid, number, company_id, branch_id, supplier_id, vendor_return_id?, cn_date, currency_code, total_amount, allocated_amount, status, notes, version, audit). State machine `POSTED → PARTIALLY_ALLOCATED → FULLY_ALLOCATED`.
  - `vendor_credit_note_allocation` (id, vendor_credit_note_id, supplier_invoice_id, amount, allocated_at, allocated_by). Mirror of `customer_credit_note_allocation`.
- **One new permission (V77)** — `PROCUREMENT.MANAGE_RETURN` (id **136**) — grants create + post + cancel + issue-credit + apply. Granted to ADMIN role (per V70/V74 pattern; persona widening via the FE seed).
- **Two enums** — `VendorReturnStatus { DRAFT, POSTED, CREDITED }`, `VendorCreditNoteStatus { POSTED, PARTIALLY_ALLOCATED, FULLY_ALLOCATED }`, plus reuse the existing `ReturnReason` if it lives in `modules/sales/domain/enums/` — **decision: move `ReturnReason` to `modules/common/domain/enums/`** if it's currently sales-only, OR define a parallel `VendorReturnReason` if the values diverge. Recommendation: **move to `common`** — the values (`DAMAGED`, `WRONG_ITEM`, `EXPIRED`, `OTHER`) are shared. Acceptable rename; no existing JSON consumers besides our own UI.
- **Stock impact** — `vendor_return.post()` posts a stock-OUT movement:
  - `restock = true` → existing entity is being returned to supplier from on-hand → `RETURN_OUT` move (reduces on-hand by `returned_qty`, source GRN preserved as reference if known).
  - `restock = false` → goods were damaged on receipt and never made it to sellable on-hand → `DAMAGE_DISCARD` move (or whichever stock-move type best fits — `WRITE_OFF` is the closest existing equivalent; the BE agent picks based on what `StockMoveType` already exposes).
  - Cross-module call from `procurement.VendorReturnServiceImpl` → `stock.StockMoveService`. Same shape as the AR-side `customer_return.post()` → `stock.StockMoveService` call. **Already covered by ADR-0004 (the procurement→stock seam is named there).**
- **Five endpoints under `/api/v1`** (gated by `PROCUREMENT.MANAGE_RETURN`):
  - `GET  /vendor-returns?branchId&page&size` → paged list of vendor returns.
  - `GET  /vendor-returns/uid/{uid}` → single return + lines.
  - `POST /vendor-returns` → create draft.
  - `POST /vendor-returns/uid/{uid}/post` → post (stock-OUT + transition to POSTED).
  - `POST /vendor-returns/uid/{uid}/cancel` → cancel (DRAFT only).
  - `POST /vendor-returns/uid/{uid}/issue-credit-note` → CREDITED + creates vendor_credit_note.
  - `GET  /vendor-credit-notes?branchId` → list.
  - `POST /vendor-credit-notes/uid/{uid}/apply` → apply to a supplier invoice (the **US-PROC-009 apply path**). Body: `{ supplierInvoiceUid, amount }`.
- **Three outbox events** — `VendorReturnPosted.v1`, `VendorCreditNoteIssued.v1`, `VendorCreditNoteApplied.v1`. Payloads mirror the AR side.
- **Same-tx invariant on apply** — `vendor_credit_note_allocation` row write + `vendor_credit_note.allocated_amount` increment + status transition + `supplier_invoice.paidAmount` increment + (optional invoice status flip to `PAID`) + outbox event row, all in one DB transaction. Mirror of Slice H's customer-side guarantee.
- **`SupplierInvoiceService.applyVendorCredit(invoiceId, amount)`** — new method, mirror of the existing `applyWriteOff` and the H-side `SalesInvoiceService.applyCreditNote`. Increments `paidAmount`; flips status to `PAID` if fully paid; throws if would overrun.
- **Web UI** — new `/procurement/vendor-returns` feature folder:
  - `vendor-returns.component.ts` — queue page with two tabs: "Returns" (list of vendor_return rows) and "Credit notes" (list of vendor_credit_note rows). Status pills, Apply button per credit note.
  - `vendor-return-create.component.ts` — draft creation form (pick supplier → pick GRN/supplier-invoice → line picker → save draft / post directly).
  - `vendor-credit-note-apply-modal.component.ts` — pick supplier's open invoice → enter amount → submit. Reuse the shape of `credit-note-apply-modal.component.ts` from Slice H.
  - Sidebar nav: "Vendor returns" link under Procurement section.
- **E2E spec** — new `e2e/vendor-returns.spec.ts`. Scenarios as `test.fail`:
  1. Procurement-officer creates a vendor return against a posted GRN → posts → stock on-hand reduces.
  2. Procurement-officer issues a credit note → vendor_credit_note row created.
  3. Procurement-officer applies the credit note's full amount to a different open supplier invoice → invoice `paidAmount` reflects; status flips to `PAID` if fully paid.
  4. Partial apply twice → FULLY_ALLOCATED.
  5. Over-apply → 422. Cross-supplier apply → 422. Apply to FULLY_ALLOCATED → 409.
  6. Permission 403 — `qa.sales.clerk` and `qa.cashier` get 403 on every endpoint.
  Axe-core on the new pages.

**Out:**

- **Cash refund from vendor** (instead of credit-note application) — separate slice. Today every refund is credit-only.
- **Auto-apply** the credit note against the oldest overdue supplier invoice — UX nicety deferred.
- **Multi-invoice apply** in one POST — single invoice per call.
- **Reverse / un-apply** an allocation — separate slice.
- **Returns that span multiple GRNs in one document** — single original-GRN (or no-GRN-reference) only.
- **Vendor credit notes issued WITHOUT a return** (e.g. price-adjustment credit) — out of scope; today every vendor credit note is return-driven.

## 2. Permission band

Next free: **136**. G.2 took 134-135. Slice H reused existing 34. H.1 consumes **one new id**:

| Id | Constant | Granted to (V77) |
|---|---|---|
| 136 | `PROCUREMENT.MANAGE_RETURN` | ADMIN role 1; persona widening via FE seed for `procurement-officer` + `accountant` |

Band 137+ reserved for slice fix-ups.

## 3. Schema — `V76__vendor_returns.sql` + `V77__seed_procurement_return_permission.sql`

`V76` creates the 4 tables + 4 sequences + indexes. Mirror of V30 + V75 with `customer` → `supplier`, `sales_invoice` → `supplier_invoice`, `original_invoice_id` → `original_grn_id` + `original_supplier_invoice_id`. **Use `VARCHAR(26)` for any uid reference column** (lesson learned from Slice G.2's V73 column-type mismatch).

`V77` seeds perm 136 + grants to ADMIN.

## 4. Service shape — `VendorReturnServiceImpl`

Lives in `modules/procurement/service/`. Methods mirror `CustomerReturnServiceImpl`:

```java
createDraft(CreateVendorReturnRequestDto)
post(String uid)                            // stock-OUT + emit VendorReturnPosted.v1
cancel(String uid)
issueCreditNote(String uid, IssueVendorCreditNoteRequestDto)
get(String uid), list(...), listCreditNotes(...)
applyToInvoice(String creditNoteUid, ApplyVendorCreditNoteRequestDto)
                                            // → SupplierInvoiceService.applyVendorCredit
                                            // → emit VendorCreditNoteApplied.v1
```

Tenant guard from `RequestContext.companyId` everywhere.

## 5. Frontend touchpoints

| File | Role |
|---|---|
| NEW `features/procurement/vendor-returns.component.ts` | Queue page with Returns + Credit-notes tabs. |
| NEW `features/procurement/vendor-return-create.component.ts` | Draft creation form (supplier picker, GRN/invoice ref, line list). |
| NEW `features/procurement/vendor-credit-note-apply-modal.component.ts` | Pick supplier's open invoice + amount + submit. Mirror of the H modal. |
| MOD `features/procurement/procurement.service.ts` | Add `createVendorReturn`, `listVendorReturns`, `getVendorReturn`, `postVendorReturn`, `cancelVendorReturn`, `issueVendorCreditNote`, `listVendorCreditNotes`, `applyVendorCreditNote`, `getSupplierOpenInvoices`. |
| MOD `features/procurement/procurement.models.ts` | Add 8 types: `VendorReturn`, `VendorReturnLine`, `VendorCreditNote`, `VendorCreditNoteAllocation`, `CreateVendorReturnRequest`, `IssueVendorCreditNoteRequest`, `ApplyVendorCreditNoteRequest`, `VendorReturnStatus`, `VendorCreditNoteStatus`. |
| MOD `features/procurement/procurement.routes.ts` | Add `/procurement/vendor-returns` route. |
| MOD shell sidebar | Add nav link. |

## 6. ADR-0004 — exemption inventory

Add **two rows** (the procurement → stock seam might already be exempted; check before adding row 22):

> | 22 | `procurement.VendorReturnServiceImpl` → `stock.StockMoveService` | `VendorReturnServiceImpl.java` (`post`) | **(i) Sync — ADR-0004 already named for procurement→stock (Slice B).** `vendor_return` POST transitions and the stock-OUT movement happen in one tx — the invariant "if return is POSTED, stock has decreased" must hold strictly. No new exemption needed if Slice B's procurement→stock entry already covers it. Confirmed only if `GrnServiceImpl` writes to `stock` synchronously (it does). |
> | 23 | `procurement.VendorReturnServiceImpl` → `procurement.SupplierInvoiceService` | `VendorReturnServiceImpl.java` (`applyToInvoice`) | **(i) Sync — ADR-0004 named (Slice H.1).** `vendor_credit_note_allocation` write + `vendor_credit_note.allocated_amount` update + `supplier_invoice.paidAmount` update happen in the same DB tx as the outbox `VendorCreditNoteApplied.v1` event. Mirror of row 21 (Slice H customer-side). |

If row 22 turns out to already be covered by the Slice B entry, the BE agent removes it and just adds row 23. Don't double-document.

No ADR-0005 amendment.

## 7. Task list — parallel fan-out

| # | Owner | Deliverable | Acceptance |
|---|---|---|---|
| 1 | **qa-engineer** | NEW `e2e/vendor-returns.spec.ts` — ~7 scenarios as `test.fail` (happy AP path + partial apply + 422 over-apply + 422 cross-supplier + 409 fully-allocated + 403 cashier + 403 sales-clerk). Persona check: confirm `procurement-officer` or `accountant` is the natural actor. Axe-core on the new pages. | Spec compiles, lists 7+ tests, all `test.fail`. |
| 2 | **backend-engineer** | V76 + V77 migrations; 4 entities; 4 repositories; ~8 DTOs (VendorReturnDto, VendorReturnLineDto, VendorCreditNoteDto, VendorCreditNoteAllocationDto + 3 request DTOs + page row DTOs); 2 enums; VendorReturnService + Impl per §4; 1 new flat controller `VendorReturnController` under `com.orbix.engine.api`; SupplierInvoiceService.applyVendorCredit method; 3 outbox events; ADR-0004 update (row 22 + 23, or just 23 if 22 already covered); BE unit tests on service + JSON pin on the 3 wire DTOs. | `mvn test` 622+/0 fail (HealthSmokeTest 1 known err). |
| 3 | **frontend-engineer** | All files in §5; unit specs for queue + create + apply modal + service; reuse existing modal pattern from `credit-note-apply-modal.component.ts`. | `npm test` green; `npm run build` green. |
| 4 | **integration** | Cherry-pick (if worktree isolation produces separate branches), validate, QA-image rebuild, smoke. | All green. |

## 8. Risks / things to watch

- **`ReturnReason` location** — if it lives in `modules/sales/domain/enums/`, the BE agent must move it to `modules/common/domain/enums/` (or `procurement` if there's no common bucket for cross-module enums) and update both sales + procurement imports. ArchUnit will complain if procurement imports from sales. **This is the most likely cross-cutting issue.**
- **Column-type mismatch repeat from G.2** — every `*_uid` column on the new tables must use `VARCHAR(26)` (NOT `CHAR(26)`) because the entities will declare `@Column(length=26)` without `columnDefinition`. Bake this into the migration on first write.
- **`StockMoveType` enum** — confirm `RETURN_OUT` and `DAMAGE_DISCARD` (or equivalents) exist. If not, the BE agent picks the closest existing types and notes the gap as a follow-up.
- **GRN ↔ vendor_return link** — `vendor_return.original_grn_id` is **optional** (returns without a GRN reference are common — damaged on delivery before GRN was raised). Keep nullable.

---

**Total estimate**: ~1.5–2 days. Larger than H because we're doing the full mirror in one slice (the customer side took two: V30 + Slice H). Same shape, different tables.
