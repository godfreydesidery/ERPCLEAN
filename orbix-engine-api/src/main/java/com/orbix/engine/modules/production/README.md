# Production module

## 1. Purpose

`production` owns **in-house manufacture**: bakery dough → loaves, raw chicken → cuts, fruit → fresh juice, etc. It turns materials (catalog items with `type ∈ {CONSUMABLE, BOTH}`) into finished output (`type ∈ {SELLABLE, BOTH}`) by recipe (`bom`), tracks the actual consumption + yield + wastage, and stamps each output with a `stock_batch` carrying manufacture and expiry dates.

This module was deferred in the initial MVP scope and is re-added in Phase 1.1 because the supermarket runs in-house bakery + juice + butchery + deli.

## 2. Scope

In scope:
- Recipe definition: `bom` + `bom_line`, including **sub-recipes** (`parent_bom_id` chains — bread dough is a sub-BOM consumed by many final breads).
- Batch lifecycle: PLANNED → IN_PROGRESS → OUTPUT_HOT_DISPLAY → OUTPUT_COLD_DISPLAY → OUTPUT_DISCOUNTED → OUTPUT_DONATED → OUTPUT_WRITE_OFF → CLOSED.
- Material reservation when a batch starts (prevents oversell of the same flour to two batches).
- Actual consumption capture (`production_consumption`) — usually equals planned, may vary.
- Actual output capture (`production_output`) — emits `stock_batch` rows for in-house items.
- Pack-by-weight workflow (items packed to variable weight at production time).
- Wastage tracking with category + reason.
- Custom production (no BOM, ad-hoc).
- Conversions (material → material, product → product, product → material).
- Yield variance reporting.

Out of scope (owned elsewhere):
- Stock balance updates — `stock` consumes `ProductionConsumePosted.v1` and `ProductionOutputPosted.v1` to write `stock_move` rows.
- Item master and BOM-eligibility flags on items — `catalog`.
- Section context (which section runs which BOM) — owned here via `bom.section_id`, but the section master is in `admin`.
- Cost accounting of finished output — `stock` recomputes moving average from the output cost emitted on `ProductionOutputPosted.v1`.

## 3. Domain model

| Aggregate / table | Purpose |
|---|---|
| `bom` | Recipe header. Versioned (`version`, `effective_from`). `parent_bom_id` chains sub-recipes. `section_id` owns it. Status: `DRAFT`, `ACTIVE`, `RETIRED`. |
| `bom_line` | One row per material × qty + standard wastage %. May reference another BOM (sub-recipe). |
| `production_batch` | A single planned/executed run. Status: `PLANNED`, `IN_PROGRESS`, `OUTPUT_HOT_DISPLAY`, `OUTPUT_COLD_DISPLAY`, `OUTPUT_DISCOUNTED`, `OUTPUT_DONATED`, `OUTPUT_WRITE_OFF`, `CLOSED`. Carries `section_id`. |
| `production_consumption` | Actual material drawn for a batch. One row per material; rolls up to a `stock_move` of type `PROD_CONSUME`. |
| `production_output` | Actual finished output. One row per output item; rolls up to `stock_move` of type `PROD_OUTPUT` + a fresh `stock_batch` row. `is_pack_by_weight` flag drives variable-weight packing. |
| `production_wastage` | Category-tagged loss recorded against a batch: `BURNT`, `EXPIRED`, `DROPPED`, `SAMPLED`, `DONATED`, `OTHER`. Mandatory `reason`. |
| `conversion` | Standalone non-BOM transformation (e.g. bulk flour → packed flour). Emits both consume and output moves in one transaction. |

See [DATA-MODEL.md §9](../../../../../../../../DATA-MODEL.md).

## 4. Key business flows

1. **Define / version a BOM (US-PROD-001, US-PROD-002).** Author lists materials, quantities, standard wastage %, yield. Sub-recipes referenced by `parent_bom_id`. New version supersedes previous via `effective_from`; old `production_batch` rows reference the version they used.
2. **Plan a batch (US-PROD-003).** Pick `bom_id` + target output qty. System explodes BOM (and any sub-BOMs) and reserves materials via a `stock_move` of type `RESERVED`. Emits `ProductionBatchPlanned.v1`.
3. **Start batch (US-PROD-004).** Reserved materials become `PROD_CONSUME` moves; `production_batch.lifecycle_state` → `IN_PROGRESS`. Emits `ProductionBatchStarted.v1`.
4. **Record actual consumption + output (US-PROD-005).** Operator confirms qty consumed (may differ from plan); records output qty per item. Each output creates a `stock_batch` row with `manufactured_at = now`, `expiry_at` set by item / batch policy. Emits `ProductionConsumePosted.v1` (× N) + `ProductionOutputPosted.v1` (× M).
5. **Record wastage.** Mandatory category + reason. Wastage qty does NOT enter stock; it's logged for reporting and variance analysis.
6. **Move output through lifecycle.** As the day progresses, batch lifecycle moves from `OUTPUT_HOT_DISPLAY` (just baked) → `OUTPUT_COLD_DISPLAY` (cooled, on shelf) → `OUTPUT_DISCOUNTED` (near end of day) → `OUTPUT_DONATED` / `OUTPUT_WRITE_OFF` (end of day if unsold). Each transition logged; the `OUTPUT_DONATED` / `OUTPUT_WRITE_OFF` transitions trigger compensating `stock_move` rows.
7. **Custom production (US-PROD-006).** No BOM. Operator records consumption + output ad-hoc. Same event types fire; `production_batch.bom_id` is NULL.
8. **Conversion (US-PROD-007).** Single-step item-to-item transform via `conversion` table. Stock module writes paired moves in one transaction.

## 5. Module interactions

**Depends on:**
- `catalog` — `item` references for materials + outputs. Items with `tracks_batches = true` get a `stock_batch` row on output.
- `stock` — read-only on-hand check at plan time; downstream writes `stock_move` rows on consume/output events.
- `admin` — `section_id` for BOM and batch ownership.
- `day` — production can only post into an OPEN business day.
- `common` — outbox, audit.

**Publishes events:**
- `BomDefined.v1`, `BomVersioned.v1`, `BomRetired.v1`
- `ProductionBatchPlanned.v1`, `ProductionBatchStarted.v1`
- `ProductionConsumePosted.v1`, `ProductionOutputPosted.v1`
- `ProductionWastageRecorded.v1`
- `ProductionLifecycleAdvanced.v1` (carries new `lifecycle_state`)
- `ProductionBatchClosed.v1`
- `ConversionPosted.v1`

**Consumes events:**
- None typical. Production is a source module.

## 6. API surface

| Resource | Endpoints |
|---|---|
| `/api/v1/boms` | `GET`, `POST`, `GET/PATCH /{id}`, `POST /{id}/retire`, `POST /{id}/version` |
| `/api/v1/production-batches` | `GET`, `POST` (plan), `POST /{id}/start`, `POST /{id}/post-output`, `POST /{id}/advance-lifecycle`, `POST /{id}/close` |
| `/api/v1/production-wastage` | `POST` — record against a batch |
| `/api/v1/conversions` | `POST` — post a standalone conversion |
| `/api/v1/reports/production-variance` | `GET` — planned vs actual, yield variance, wastage breakdown |

## 7. Persistence

- Flyway: `V4__production_bom_and_batch.sql`, `V4_1__production_wastage_and_conversion.sql` under `common/`.
- Sequences (added in `V1_3__phase11_sequences.sql`): `bom_seq`, `bom_line_seq`, `production_batch_seq`, `production_consumption_seq`, `production_output_seq`, `production_wastage_seq`.
- Per-branch sequences for batch number: `BATCH-BR1-000123`.

## 8. User stories

**P1 (MVP for bakery/juice/butchery):**
- US-PROD-001 — Define a BOM
- US-PROD-002 — Version a BOM
- US-PROD-003 — Plan a batch
- US-PROD-004 — Start a batch
- US-PROD-005 — Record actual material consumption and finished output
- US-PROD-006 — Run a custom production (no BOM)
- US-PROD-009 — Record wastage with category and reason

**P2:**
- US-PROD-007 — Record a conversion between items
- US-PROD-008 — Run a production variance report
- US-PROD-010 — Advance batch lifecycle (hot → cold → discounted → donated/write-off)
- US-PROD-011 — Pack-by-weight at output
- US-PROD-012 — Sub-recipe references (bread dough used in many final breads)

## 9. Open questions

- **Sub-recipe explosion at plan vs at start** — explode at plan (locks materials early) or at start (more accurate to actual sub-BOM yield)? Default: explode at plan; revise on start if actual sub-BOM yield differs.
- **Pack-by-weight expected packs** — for variable-weight outputs, how does the operator declare pack count vs total weight? Pick one.
- **Section operating a BOM** — can the same BOM be operated by two sections (bakery in branch A, central kitchen in branch B)? Currently one section per BOM; cross-section means a separate BOM row.

## 10. Implementation notes

- **Layered shape:** `domain/{entity,dto,enums,event}/`, `service/`, `repository/`.
- **Lombok:** `@Data` on entities with `@NoArgsConstructor(access = PROTECTED)`, `@EqualsAndHashCode(of = "id")`. Business methods (e.g. `advanceLifecycle()`, `recordWastage()`) coexist with generated setters.
- **Invariants:**
  - BOM circular reference forbidden (`parent_bom_id` chain must terminate).
  - `production_batch` cannot start unless all materials are reservable.
  - `production_output.qty` ≤ planned-output-qty × (1 + max_yield_variance) — soft warning, hard reject above `2× planned`.
  - `production_wastage` row requires non-empty `reason`.
  - Lifecycle is forward-only except `OUTPUT_DONATED` / `OUTPUT_WRITE_OFF` from any `OUTPUT_*` state.
  - Closed batch (`CLOSED`) is immutable.
- **Multi-tenancy:** every row carries `company_id` + `branch_id` (via the batch); `bom.section_id` is required.
- **Idempotency:** every state transition accepts `Idempotency-Key`; replays return the prior result.
- **Outbox:** consume/output events in same transaction as the writes. `stock` consumer is idempotent on `(production_batch_id, item_id, line_seq)`.
- **Costing:** `production_output.cost` is computed at post time as `sum(consumed cost) ÷ total_output_qty` (proportional split by qty). Variance vs `bom.standard_cost` surfaces in the variance report.
