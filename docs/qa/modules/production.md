# Production — test plan

BOM, sub-recipes, batch lifecycle, consumption, output, wastage, conversion. Section-scoped.

## BOM

### TC-PROD-001 — Define BOM [P1]
**Stories:** US-PROD-001
**Steps:** POST /api/v1/boms with materials (item, qty, standard wastage), output item, section.
**Expected:** 201; status DRAFT; activated separately; `BomDefined.v1`.

### TC-PROD-002 — Version a BOM [P1]
**Stories:** US-PROD-002
**Steps:** POST /boms/{id}/version creating new version with `effective_from`.
**Expected:** New row; old version retired at `effective_from − 1`; historical batches keep their version reference.

### TC-PROD-003 — Sub-recipe (parent_bom_id) [P1]
**Stories:** US-PROD-012
**Steps:** Bread-Dough BOM consumed by 3 final-bread BOMs.
**Expected:** Each final BOM has `parent_bom_id = null`; line referencing Bread-Dough has type = sub-BOM. Allowed.

### TC-PROD-004 — Circular sub-recipe rejected [P1]
**Steps:** A consumes B, B consumes A.
**Expected:** 422 at create time.

### TC-PROD-005 — BOM with no section_id [P1]
**Steps:** POST BOM without section.
**Expected:** 422 — section required.

## Plan + start

### TC-PROD-006 — Plan a batch [P1]
**Stories:** US-PROD-003
**Steps:** POST /production-batches with bom_id + target_qty.
**Expected:** Status PLANNED; materials reserved via stock_move RESERVED. `ProductionBatchPlanned.v1`.

### TC-PROD-007 — Insufficient materials at plan time [P1]
**Steps:** Plan exceeds available stock.
**Expected:** 422; no reservation created.

### TC-PROD-008 — Start batch [P1]
**Stories:** US-PROD-004
**Steps:** POST /batches/{id}/start.
**Expected:** Status IN_PROGRESS; reservation flips to PROD_CONSUME moves; `ProductionBatchStarted.v1` + `ProductionConsumePosted.v1`.

## Output + wastage

### TC-PROD-009 — Record output [P1]
**Stories:** US-PROD-005
**Steps:** POST /batches/{id}/post-output with actual consumption + output.
**Expected:** PROD_OUTPUT moves; if output item `tracks_batches`, new stock_batch created with manufactured_at = now; `ProductionOutputPosted.v1`.

### TC-PROD-010 — Output qty within yield tolerance [P1]
**Steps:** Actual output 48 of planned 50 (4% under).
**Expected:** Allowed; soft warning surfaced in response.

### TC-PROD-011 — Output qty 2x planned blocked [P1]
**Steps:** Actual output 110 of planned 50.
**Expected:** 422 unless override.

### TC-PROD-012 — Record wastage with category [P1]
**Stories:** US-PROD-009
**Steps:** POST wastage `{ batch_id, item_id, qty: 3, category: DONATED, reason: "Food bank" }`.
**Expected:** 201; `ProductionWastageRecorded.v1`. Wastage does NOT enter stock (it's a side-channel record).

### TC-PROD-013 — Wastage missing reason [P1]
**Steps:** Empty reason.
**Expected:** 422.

### TC-PROD-014 — Category not in enum [P1]
**Steps:** category = "STOLEN".
**Expected:** 422.

## Lifecycle

### TC-PROD-015 — Advance lifecycle hot→cold→discounted [P1]
**Stories:** US-PROD-010
**Steps:** POST /batches/{id}/advance-lifecycle to OUTPUT_COLD_DISPLAY, then OUTPUT_DISCOUNTED.
**Expected:** Forward transitions; `ProductionLifecycleAdvanced.v1` each step.

### TC-PROD-016 — Backward transition rejected [P1]
**Steps:** From DISCOUNTED back to HOT_DISPLAY.
**Expected:** 422.

### TC-PROD-017 — Donate / write-off from any OUTPUT_* state [P1]
**Steps:** From COLD_DISPLAY → OUTPUT_DONATED.
**Expected:** Allowed; compensating stock_move out for remaining qty; `ProductionLifecycleAdvanced.v1` (terminal).

### TC-PROD-018 — Close batch [P1]
**Steps:** POST /batches/{id}/close after all output disposed.
**Expected:** Status CLOSED; immutable.

## Custom + conversion

### TC-PROD-019 — Custom production (no BOM) [P2]
**Stories:** US-PROD-006
**Steps:** POST batch with bom_id = null + free-form consumption + output.
**Expected:** Allowed; events fired; no BOM variance to compute.

### TC-PROD-020 — Conversion [P2]
**Stories:** US-PROD-007
**Steps:** POST /conversions: bulk-flour → packed-flour, paired consume + output.
**Expected:** Both moves in same tx; `ConversionPosted.v1`.

## Variance + reporting

### TC-PROD-021 — Yield variance report [P2]
**Stories:** US-PROD-008
**Steps:** GET /reports/production-variance.
**Expected:** Per-BOM rows with planned vs actual, wastage breakdown by category.

### TC-PROD-022 — Recipe costing [P3]
**Steps:** GET BOM cost estimate.
**Expected:** Sum(line.qty × material.avg_cost) ÷ standard_output_qty.

## Pack-by-weight

### TC-PROD-023 — Output is_pack_by_weight [P2]
**Stories:** US-PROD-011
**Steps:** Output line `is_pack_by_weight = true`; qty in KG.
**Expected:** stock_batch carries actual weight; pack count separately recorded (or implicit).

## Section-scoping

### TC-PROD-024 — Plan / start / output stamp section_id [P1]
**Stories:** US-PROD-013
**Steps:** Batch from BOM in BAKERY section.
**Expected:** `production_batch.section_id = BAKERY`; stock moves stamped; reporting filters by section.

### TC-PROD-025 — Output to retail floor flips section on transfer [P2]
**Steps:** Bakery posts 100 loaves; manager transfers to RETAIL_FLOOR for display.
**Expected:** Section-to-section stock_move; output remains in bakery batch row; floor balance += loaves.

## Day-gating

### TC-PROD-026 — Production on closed day blocked [P1]
**Steps:** Branch day CLOSED. Plan / start / output.
**Expected:** 422 `BUSINESS_DAY_CLOSED`.

## Edge

### TC-PROD-027 — Concurrent starts on same batch [P2]
**Steps:** Two clients start the same PLANNED batch.
**Expected:** Exactly one transitions to IN_PROGRESS; other 409.

### TC-PROD-028 — Idempotent output post [P1]
**Steps:** POST /post-output twice with same Idempotency-Key.
**Expected:** Same response; one set of stock moves.
