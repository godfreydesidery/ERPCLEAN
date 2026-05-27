# Slice B — Procurement Hardening Gap Audit

Section-by-section diff of the procurement aggregates against `docs/conventions/hardening-checklist.md`. Backend-engineer uses this to drive the implementation; PM uses it to scope the slice.

## Aggregates audited
- `LpoOrder`, `LpoOrderLine`, `Grn`, `GrnLine`
- Source: `orbix-engine-api/src/main/java/com/orbix/engine/modules/procurement/`
- Migrations: `V19__lpo_orders.sql`, `V19_1__lpo_sequences.sql` (mysql + postgres), `V20__seed_procurement_permissions.sql`, `V21__grns.sql`, `V21_1__grn_sequences.sql`, `V22__seed_grn_permissions.sql`
- Out-of-scope (deferred to Slice C, not audited here): `SupplierInvoice`, `SupplierInvoiceGrn`.

## Section 1 — Data layer
- [x] Pre-stability migrations edit existing baselines (`V19`/`V21`), per ephemeral-migrations policy.
- [x] No native SQL features; pure DDL portable across MariaDB/Postgres. Sequences split per-dialect (`mysql/V19_1`, `postgres/V19_1`).
- [x] `uid CHAR(26) NOT NULL` + `uk_lpo_order_uid` / `uk_grn_uid` constraints on both header tables (`V19__lpo_orders.sql:6,26`; `V21__grns.sql:8,28`).
- [x] Multi-tenant: `lpo_order` and `grn` carry `company_id BIGINT NOT NULL` + `branch_id BIGINT NOT NULL` with FKs (`V19:8-9,28-29`; `V21:10-11,30-31`).
- [x] `@Id BIGINT` via SEQUENCE strategy, one sequence per table, `allocationSize = 50` (`LpoOrder.java:29-30`, `LpoOrderLine.java:22-23`, `Grn.java:29-30`, `GrnLine.java:21-22`). Dialect sequences live in `V19_1` / `V21_1`.
- [x] FKs on every cross-table ref, named `fk_<child>_<parent>`, default `NO ACTION` (`V19:28-31`, `V21:30-33,51-55`).
- [x] Audit columns + `@Version` on header rows (`V19:21-25`, `V21:23-27`; entity `@Version` at `LpoOrder.java:76-77`, `Grn.java:77-78`).
- [x] ADR 0002 (composite-PK uid pattern) is **not applicable** here — both aggregates are surrogate Long-PK, so the standard pattern applies. Cite explicitly so reviewers don't ask.
- [ ] **GAP 1.A**: `lpo_order_line` and `grn_line` have **no `company_id` / `branch_id` column** (`V19:37-54`, `V21:39-56`). Lines inherit tenancy through their parent header, which is acceptable per ARCHITECTURE.md §2.2 for child rows of a tenant-scoped aggregate — but the README "Multi-tenant scoping" section (`procurement/README.md §10`) claims **"every row carries company_id and branch_id"**. Backend-engineer action: update the module README to state the inheritance explicitly (the line tables are intentionally not denormalised), so the audit trail of conventions stays honest. No schema change.
- [ ] **GAP 1.B**: Indexes are thin. `grn (lpo_order_id)` is indexed (`V21:37`) but **no index on `grn (supplier_id)`** despite `findByCompanyIdAndBranchIdAndReceivedDateAndStatus` in `GrnRepository:35-36` driving the F8.2 daily summary, and **no composite `(company_id, branch_id, received_date, status)`** for that same hot query. `lpo_order` has the symmetric gap — `findByCompanyIdAndBranchIdOrderByIdDesc` walks a non-indexed pair. Backend-engineer action: add `ix_grn_supplier_id`, `ix_grn_branch_received_status (branch_id, received_date, status)`, and confirm `ix_lpo_order_branch_status` covers paged list reads.
- [ ] **GAP 1.C**: `grn_line` has **no FK on `lpo_order_line_id`** index (`V21:57` only indexes `grn_id`). Three-way match (Slice C) will join on `lpo_order_line_id` heavily; adding `ix_grn_line_lpo_line` now avoids a follow-up migration. Backend-engineer action: add the index in the existing baseline.

## Section 2 — Domain layer (DTOs + enums)
- [x] All DTOs end with `Dto` including nested records (`CreateLpoOrderRequestDto.Line`, `CreateGrnRequestDto.Line`).
- [x] DTOs are Java `record`s, immutable; entities use Lombok `@Data` (`LpoOrder.java:23`, `Grn.java:23`).
- [x] Response DTOs include both `id` and `uid` (`LpoOrderDto.java:13-14`, `GrnDto.java:13-14`); JSON pin tests `LpoOrderDtoJsonTest` + `GrnDtoJsonTest` exist.
- [x] Enums in `domain/enums/`, `@Enumerated(EnumType.STRING)` (`LpoOrder.java:63`, `Grn.java:64`).
- [ ] **GAP 2.A**: Bean validation is **thin** on monetary and string fields. `CreateLpoOrderRequestDto.Line.unitPrice` has `@PositiveOrZero` but no `@Digits(integer=14, fraction=4)` to bound DB precision; `number` is `@NotBlank` but lacks `@Size(max=40)` (DB col is `VARCHAR(40)`); `currencyCode` lacks `@Size(min=3,max=3)` / `@Pattern("[A-Z]{3}")`; `notes` lacks `@Size(max=2000)`; `discountPct` lacks `@DecimalMin("0")` / `@DecimalMax("100")`. Same shape on `CreateGrnRequestDto`: `receivedQty` `@Positive` but no `@Digits`; `batchNo` lacks `@Size(max=40)`; `supplierDeliveryNote` lacks `@Size(max=80)`. Backend-engineer action: add the `@Size`/`@Digits`/`@Pattern` constraints across both request DTOs and the line records.

## Section 3 — Service layer
- [x] `LpoOrderService` + `LpoOrderServiceImpl`, `GrnService` + `GrnServiceImpl` pairs; impls are `@Service @RequiredArgsConstructor` with `final` collaborators.
- [x] Every public method has a `@Transactional` boundary (`LpoOrderServiceImpl.java:58,82,93,112,126,137,148`; `GrnServiceImpl.java:67,192,248,259,270`).
- [x] Writes carry `@Auditable` (CREATE, UPDATE, SUBMIT, APPROVE, CANCEL on LPO; CREATE, POST, CANCEL on GRN).
- [x] External entry points take `String uid`; internal joins on `Long id` stay in service (`requireOrderByUid` at `LpoOrderServiceImpl:192`, `requireGrnByUid` at `GrnServiceImpl:276`).
- [x] Tenant predicate enforced — `!Objects.equals(getCompanyId(), context.companyId()) → NoSuchElementException` on both aggregates.
- [x] Outbox emission via `EventPublisher.publish(...)` in the same `@Transactional` write — no direct service-to-service calls cross-module (stock writes are the documented exception, see GAP 9.B below).
- [ ] **GAP 3.A**: There is **no `requireOrderById(Long)` helper** alongside `requireOrderByUid(String)` (`LpoOrderServiceImpl`). When the three-way match in Slice C joins `supplier_invoice_grn` rows and needs to read LPO context from a numeric id, the consumer will either bypass the tenant check or duplicate the predicate. The checklist explicitly calls for the dual helper pattern (see `PriceListServiceImpl.requireItemById`). Backend-engineer action: add `private LpoOrder requireOrderById(Long)` that runs the same tenant check (the existing `GrnServiceImpl.requireLpo` already does this — promote/duplicate the pattern to `LpoOrderServiceImpl` for consistency).
- [ ] **GAP 3.B**: `GrnServiceImpl.cancel(...)` only allows DRAFT → CANCELLED (`Grn.java:125-129`). The PM-locked decision adds `POST /api/v1/grns/uid/{uid}/cancel-posted` for POSTED → CANCELLED (compensating). Backend-engineer action: add `GrnService#cancelPosted(String uid, String reason)` + `@Auditable(action="CANCEL_POSTED")` + new compensating outbox event (see GAP 9.A). `reason` is required (length-bounded, validated upstream); record it on the GRN via a new `cancellation_reason` column (Section 1 schema change) OR carry it only on the outbox payload — recommend the column so the audit trail is queryable without joining `domain_event`.
- [ ] **GAP 3.C**: `LpoOrder.cancel(...)` (`LpoOrder.java:145-151`) allows DRAFT and PENDING_APPROVAL only, throwing on APPROVED. The README §4.8 says "LPO cancellation: only if no posted GRN draws against it" — i.e. APPROVED with no GRNs should cancel. Today APPROVED LPOs cannot cancel at all. PM has locked the PARTIALLY_RECEIVED → CANCELLED chain to Slice C, but the APPROVED-no-GRN case is part of Slice B per `US-PROC-012`. Backend-engineer action: relax `LpoOrder.cancel(...)` to also accept APPROVED, with a service-layer guard that rejects if any `Grn` row references the LPO (`GrnRepository.findByLpoOrderId(...)` exists implicitly via the `ix_grn_lpo` index — add the finder method, or add `existsByLpoOrderId`).

## Section 4 — Repository layer
- [x] All repositories extend `JpaRepository<X, Long>`; no `@Query(nativeQuery = true)`. The lone `@Query` in `GrnLineRepository.aggregateInputVat` (lines 26-42) is portable JPQL.
- [x] `findByUid` on `LpoOrderRepository:16` and `GrnRepository:17`.
- [x] Existence/lookup methods named after the columns (`existsByBranchIdAndNumber`, `findByCompanyIdAndBranchIdOrderByIdDesc`).
- [x] No repository accessed from a controller (`ModuleBoundaryTest` enforces).
- [ ] **GAP 4.A**: `LpoOrderRepository` has no `existsByLpoOrderIdInGrn` equivalent; `GrnRepository` lacks `existsByLpoOrderId(Long)`. Needed for GAP 3.C (cancel guard) and useful for the Slice C three-way-match preview. Backend-engineer action: add `boolean existsByLpoOrderId(Long lpoOrderId)` to `GrnRepository`.

## Section 5 — REST layer
- [x] URL shape `/api/v1/lpos/uid/{uid}` and `/api/v1/grns/uid/{uid}` — literal `uid` segment present (`LpoOrderController.java:36-71`, `GrnController.java:41-63`).
- [x] All `{uid}` annotated `@ValidUlid`.
- [x] Writes gated by `@PreAuthorize("hasAuthority('...')")` — `PROCUREMENT.MANAGE_LPO` / `PROCUREMENT.APPROVE_LPO` on LPO; class-level `GRN.POST` on `GrnController` plus the runtime `GRN.DIRECT` check at `GrnController:48-50`.
- [x] `@Validated` at controller type level + `@Valid` on bodies.
- [x] No manual `ApiResponse` wrapping; `POST` returns `ResponseEntity.created(URI.create("/api/v1/<resource>/uid/" + uid))` on both.
- [x] State-transitions are `POST .../uid/{uid}/{action}` — `/submit`, `/approve`, `/cancel`, `/post`.
- [x] Controllers in `com.orbix.engine.api` (flat).
- [ ] **GAP 5.A**: All state-transition endpoints return the **full DTO** (`200 OK` with body), not `204 No Content` as the checklist states (`LpoOrderController:55-71`, `GrnController:55-63`). This is consistent with `Catalog/Price-list` precedent (which also returns the DTO post-transition) but **inconsistent with the checklist line "returning 204 No Content"**. Open question: do we update the checklist to allow `200 DTO` (which gives the client the new status without a follow-up GET), or change the controllers? Recommend **updating the checklist** — Catalog has shipped this shape and the round-trip-save is a real UX win. Backend-engineer action: none, pending the checklist clarification (escalate to PM/orchestrator).
- [ ] **GAP 5.B**: `GrnController` needs new endpoint `POST /api/v1/grns/uid/{uid}/cancel-posted` with `@PreAuthorize("hasAuthority('GRN.CANCEL')")` and a request body carrying `@NotBlank @Size(max=500) String reason`. Backend-engineer action: add the endpoint, plumb through to `GrnService#cancelPosted`.

## Section 6 — Permissions
- [x] Seed migrations follow the `V<N>__seed_<feature>_permissions.sql` pattern (`V20`, `V22`).
- [x] Permission codes follow `MODULE.ACTION` convention (`PROCUREMENT.MANAGE_LPO`, `PROCUREMENT.APPROVE_LPO`, `GRN.POST`, `GRN.DIRECT`).
- [x] Permission ids 25-28 sit in a stable band, granted to role 1 (ADMIN).
- [ ] **GAP 6.A**: **`Permissions.java` is missing every procurement constant.** `Permissions.java:1-86` has IAM, admin, catalog, party, day, stock, cash, POS sections — **no `procurement` section, no `GRN_*` constants**. Controllers today use string literals (`@PreAuthorize("hasAuthority('PROCUREMENT.MANAGE_LPO')")` etc.). Catalog hardening introduced the constants pattern (see `PRICE_LIST_*` / `PRICE_SET` / `PRICE_APPROVE`). Backend-engineer action: add a `// ---- procurement ----` section to `Permissions.java` with `PROCUREMENT_MANAGE_LPO`, `PROCUREMENT_APPROVE_LPO`, `GRN_POST`, `GRN_DIRECT`, and **the new `GRN_CANCEL`**. Update controller `@PreAuthorize` expressions to reference the constants (string-literal Spring SpEL doesn't support, but the IDE/grep navigability of central constants is the win — keep the literal in SpEL, the constant for non-annotation uses).
- [ ] **GAP 6.B**: New migration `V<next>__seed_grn_cancel_permission.sql` for `GRN.CANCEL` id `113`. The PM-locked id is 113; current high-water across all `V*permission*.sql` migrations is **74 (PRICE.APPROVE)** with permission_seq bumped past 100 in `V4_1`. **Band 75-112 is free** — backend-engineer must run the collision check (`grep -EHn "^\s*\([0-9]+, '" ...V*permission*.sql`) once more **before** picking 113 to confirm no merge to main has filled the gap. Recommend explicitly leaving 75-100 reserved for prior bands (party=80-91, day=92-93) and starting fresh procurement-cancel band at 113 — that band is empty today. Backend-engineer action: add migration inserting `(113, 'GRN.CANCEL', '...', 'procurement')` and grant to role 1.

## Section 7 — Tests
- [x] `LpoOrderServiceImplTest` + `GrnServiceImplTest` exist, `@ExtendWith(MockitoExtension.class)`, repository + `RequestContext` mocked. Cover happy path, validation (over-receipt, batch-required), lifecycle (submit/auto-approve/approve/cancel; create/post/cancel for GRN), tenant isolation, and the auto-approval threshold branch.
- [x] `@PrePersist` bypassed via `ReflectionTestUtils.setField(entity, "uid", UidGenerator.next())` (`LpoOrderServiceImplTest:241,272`, `GrnServiceImplTest:319`).
- [x] JSON wire-shape pins exist: `LpoOrderDtoJsonTest`, `GrnDtoJsonTest`.
- [x] Outbox emission asserted (`verify(events).publish(eq("LpoOrderCreated.v1"), ...)` etc.).
- [ ] **GAP 7.A**: **No test asserts the `GrnPosted.v1` payload shape.** The verification at `GrnServiceImplTest:227` is `verify(events).publish(eq("GrnPosted.v1"), any(), any(), any())` — type only, no payload keys checked. Slice C consumers (stock, debt) will key off `lineCount`, `totalAmount`, and the (proposed) `lines: [...]` array — a payload regression today is invisible. Backend-engineer action: add an `ArgumentCaptor<Map<String,Object>>` on the payload and assert the documented keys (`grnId`, `number`, `supplierId`, `totalAmount`, `lineCount`, and the new `lines[]` once GAP 9.A lands).
- [ ] **GAP 7.B**: **No test for the new `cancelPosted` flow.** Once GAP 3.B + 5.B land, backend-engineer adds tests that (a) reject `cancelPosted` on non-POSTED status, (b) emit `GrnCancelled.v1` with `compensating: true`, `priorStatus: "POSTED"`, `reason: "..."`, (c) check `GRN.CANCEL` permission is required at the controller (web-MVC slice test or a contract test).
- [ ] **GAP 7.C**: `LpoOrderServiceImplTest.cancel_fromApproved_isRejected` (line 226-234) will need to flip once GAP 3.C lands — APPROVED-no-GRN must now cancel successfully, and a new test must cover APPROVED-with-GRN → rejected.
- [ ] **GAP 7.D**: No `ModuleBoundaryTest` failure mentioned in commit history for procurement → stock direct calls (`GrnServiceImpl` injects `StockMoveService`, `StockBatchService`). The catalog/price-list precedent uses outbox only. **Verify this is intentional** — if procurement is exempt (because stock writes are synchronous, on the same TX), document it. Backend-engineer action: run `mvn -pl orbix-engine-api test -Dtest=ModuleBoundaryTest` and confirm green; if green, add a one-line comment to `GrnServiceImpl` explaining why direct injection is allowed (synchronous stock-ledger write is part of the GRN-post invariant, not a cross-module side effect). If red, raise back to solutions-architect — this needs an ADR.

## Section 8 — Web (Angular)
- [ ] **Out of scope for backend-engineer.** Audit deferred to web-engineer in Slice B task 6. Likely gaps (sight-unseen, based on the catalog precedent):
  - Procurement feature pages do not yet exist under `orbix-engine-web/src/app/features/procurement/`.
  - Web models must declare `id: string` and `uid: string`; existing legacy pages (if any) use numeric `id`.
- Surface this as an open ticket for the web-engineer; backend can confirm endpoint shape (URL + DTO) is stable before the web pass starts.

## Section 9 — Cross-module events
- [x] Versioned event types: `LpoOrderCreated.v1`, `LpoOrderSubmitted.v1`, `LpoOrderApproved.v1`, `LpoOrderCancelled.v1`, `GrnCreated.v1`, `GrnPosted.v1`, `GrnCancelled.v1`.
- [x] Payload is `Map<String, Object>` with stable keys; emission is inside the `@Transactional` write.
- [ ] **GAP 9.A** (load-bearing for Slice C): **`GrnPosted.v1` payload is too narrow.** Current keys: `grnId, number, supplierId, totalAmount, lineCount` (`GrnServiceImpl:239-243`). Stock module today reads only `grnId` and re-fetches via `StockMoveService` synchronous call — fine. But the Slice C debt-entry opener will need per-line breakdown (`itemId`, `qty`, `unitCost`, `vatGroupId`, `lineTotal`) to compute the supplier sub-ledger entry without re-reading the GRN. Backend-engineer action: widen `GrnPosted.v1` to add `lines: List<Map<String,Object>>` with per-line keys. Documented in the README §5 update.
- [ ] **GAP 9.B** (load-bearing for Slice C): **`GrnCancelled.v1` needs compensating-event semantics.** When backend-engineer adds POSTED-cancel (GAP 3.B), the same `GrnCancelled.v1` type is emitted twice: once for DRAFT-cancel (no stock impact) and once for POSTED-cancel (compensating reverse stock + reverse debt). Subscribers need to distinguish. Backend-engineer action: add `compensating: boolean`, `priorStatus: "DRAFT" | "POSTED"`, `reason: String` to the payload. DRAFT-cancel keeps `compensating=false, reason=null`; POSTED-cancel sets `compensating=true`.
- [ ] **GAP 9.C**: README §5 "Published events" lists `PurchaseQuotationRaised.v1`, `LpoOrderApproved.v1`, `GrnPosted.v1`, etc. but is missing the actually-emitted `LpoOrderCreated.v1`, `LpoOrderSubmitted.v1`, `LpoOrderCancelled.v1`, `GrnCreated.v1`, `GrnCancelled.v1`. Backend-engineer action: replace the README §5 list with a table of (event, type, payload keys, emitted-from method) — same shape as catalog/README §5.

## Section 10 — Verification (QA-image smoke)
- Owned by whoever ran the QA smoke. Backend-engineer should expect to run the QA image rebuild + wipe + smoke flow on:
  - LPO list → create draft → submit (above + below threshold) → approve → cancel.
  - GRN list → create draft from approved LPO → post (verify `stock_move` rows + `lpo_order_line.received_qty` incremented + LPO status advanced) → POSTED cancel (verify compensating event).
  - Verify `domain_event` table has the new events with expected payload keys.

---

## Cross-cutting (summary for backend-engineer)

- **Outbox payload widening (GAP 9.A, 9.B)**: load-bearing for Slice C — do this before the Slice C team starts on debt or three-way match. Document in README §5.
- **Permission band**: `GRN.CANCEL` id `113` per PM lock. High-water is 74 — run the collision check at the point of adding the migration. Add the missing procurement section to `Permissions.java` (`GAP 6.A`) in the same change.
- **APPROVED-LPO cancel** (`GAP 3.C`): this **is** in scope for Slice B per US-PROC-012. The deferred-to-Slice-C case is PARTIALLY_RECEIVED → CANCELLED, not APPROVED-no-GRN → CANCELLED.
- **Index gaps** (1.B, 1.C, 4.A): add now to avoid a Slice C follow-up migration on the same tables.

## Open questions (escalate before backend acts)

1. **204 vs 200-DTO on state transitions (GAP 5.A)** — does the hardening checklist update to allow `200 DTO`, matching catalog precedent? Affects every harden slice going forward. Escalate to PM / Godfrey.
2. **POSTED-cancel `reason` storage (GAP 3.B)** — does `grn` table get a new `cancellation_reason VARCHAR(500)` column, or does reason live only in the outbox payload + audit log? Recommend the column; needs Godfrey sign-off because it touches the V21 baseline.
3. **Procurement → stock direct injection (GAP 7.D)** — is `GrnServiceImpl` injecting `StockMoveService` / `StockBatchService` a deliberate exemption from the outbox-only rule, or a latent `ModuleBoundaryTest` violation? If exempt, the rationale belongs in an ADR (synchronous stock-ledger write is part of the GRN-post invariant). Solutions-architect to confirm — **flagging as a candidate for ADR-0003 if backend-engineer can't find existing precedent**.

## What's intentionally NOT in scope (Slice B)
- Three-way match (`SupplierInvoice` ↔ `Grn` ↔ `LpoOrder`) — Slice C.
- Supplier debt module / `debt_entry` opening on GRN post — Slice C.
- LPO PARTIALLY_RECEIVED → CANCELLED chain — Slice C.
- Vendor returns, vendor credit notes, RFQ / purchase quotation — later slices.
- Web (Angular) procurement pages — separate Slice B task owned by web-engineer; backend confirms endpoint contract only.
