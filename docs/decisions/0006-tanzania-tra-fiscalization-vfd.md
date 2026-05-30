# 0006 — Tanzania TRA fiscalization via a server-side VFD adapter behind the outbox

| Field | Value |
|---|---|
| Status | Proposed |
| Date | 2026-05-30 |
| Deciders | Godfrey |
| Supersedes | — |

## Context

The counter-only POS pilot launches in Tanzania. Issuing a non-fiscal receipt for a
taxable sale is a legal offence under TRA's fiscalization regime, so the receipt/print
path cannot be designed without first deciding how a sale becomes a **fiscal receipt**.
This is a go-live blocker and we want to design it once, not per receipt screen.

### The TRA fiscal landscape (as understood; confirm with §"Open questions")

TRA operates the **EFDMS** (Electronic Fiscal Device Management System) as the central
authority server. A taxpayer fiscalizes sales through one of two device classes:

- **EFD** — a physical Electronic Fiscal Device (hardware till/printer with a TRA SAM
  module). The POS talks to the box; the box signs and prints. Hardware per till,
  GPRS SIM, vendor lock-in, weak fit for a software POS.
- **VFD** — a **Virtual Fiscal Device**: software registered with EFDMS that signs and
  posts receipts over HTTPS, no hardware. This is the modern path and the one a
  software ERP integrates with. (The broader program is sometimes called **VFMS** —
  Virtual Fiscal Management System.)

The VFD model, as far as we can establish from public integrations:

1. **One-time registration** per taxpayer/TIN+VRN with EFDMS, yielding device
   credentials (a `.pfx` / private key + a Routing/serial config). ~2 business days.
2. **Token acquisition** — the device requests a bearer token before posting receipts.
3. **Per-receipt** the device builds an XML receipt (customer TIN/VRN if provided,
   itemized lines with tax codes, totals excl/tax/incl), **RSA-signs** it with the
   device key, and POSTs it to EFDMS. EFDMS validates and the receipt becomes fiscal.
4. **Counters** the device maintains and must never duplicate or skip:
   `RCTNUM` (receipt number), `GC` (gross/grand cumulative counter), `DC`/`ZNUM`
   (daily counter, advanced by the Z report).
5. **Verification artefacts** that must appear on the printed receipt: TIN, fiscal
   receipt number, the **verification code**, and a **QR code** encoding a
   `https://verify.tra.go.tz/...` URL so a customer/auditor can verify online.
6. **Z report** — a daily close report submitted to EFDMS at end of business day;
   advances `ZNUM` and resets the daily counter.

Two hard facts shape the architecture:

- **VFD is online.** Signing/posting needs EFDMS reachable. Our POS is **offline-first**
  (`OutboxDispatcher`, `/sync/push`, `client_op_id` idempotency). So fiscalization is
  inherently a **deferred** step relative to the cash sale: the customer may walk away
  before the receipt is fiscal.
- **The signing key is a taxpayer secret.** It must not ship to till hardware in a
  shop. That alone pushes signing to the server, not the Flutter client.

### What already exists in the codebase

- `PosSale` (`modules/pos/domain/entity/PosSale.java`) already carries a
  `fiscal_signature` column (currently unused) — a fiscalization hook was anticipated.
- Sales reach the server via `SyncServiceImpl.pushBatch` → `PosSaleService.post`,
  one TX per sale, idempotent on `client_op_id`.
- The transactional outbox (`DomainEvent`, `EventPublisher`, poller) is the sanctioned
  mechanism for asynchronous cross-module side effects ([0001](0001-modular-monolith.md),
  ARCHITECTURE.md §2.10).
- The POS receipt screen (`orbix-engine-pos/lib/features/payment/receipt_screen.dart`)
  is still mock/demo; there is **no** committed fiscal print path yet — we are
  designing greenfield, not retrofitting.

This product is multi-country-capable, so the fiscal regime must be **pluggable**: a
non-TZ deployment, or a future TZ regime change, swaps an adapter without touching POS
or sales code.

## Decision

**Fiscalize on the server, asynchronously, through a new `fiscal` module that owns a
pluggable `FiscalProvider` SPI; the Tanzania `TraVfdFiscalProvider` is the first
implementation. The POS client never holds the signing key and never talks to EFDMS
directly.** Fiscalization is triggered off the existing outbox, runs after the sale is
persisted, and the verification artefacts flow back to the till for reprint.

### Integration architecture

```
POS (Flutter, offline)          orbix-engine-api (modular monolith)            TRA EFDMS
─────────────────────           ──────────────────────────────────            ─────────
sell → local receipt    ──push──► pos.PosSaleService.post (TX)
 (PROVISIONAL, no QR)              └─ emit FiscalizationRequested.v1 ──┐
                                      into domain_event (same TX)      │
                                                                       ▼
                                   common outbox poller ──► fiscal.FiscalizationService
                                                              └─ FiscalProvider.fiscalize(sale)
                                                                   = TraVfdFiscalProvider:
                                                                     build XML, RSA-sign,  ──HTTPS──► EFDMS
                                                                     POST receipt           ◄──────── verify code + RCTNUM + QR
                                                              └─ persist FiscalReceipt row
                                                              └─ stamp PosSale.fiscal_* fields
 next /sync pull ◄──fiscal status──┘  (POS shows QR on reprint once FISCALIZED)
```

1. **New module `com.orbix.engine.modules.fiscal`.** Owns the SPI, the TRA adapter,
   the `fiscal_receipt` aggregate, device/credential config, and the Z-report job.
   It depends on `pos` only via published DTOs/enums (`ModuleBoundaryTest` holds).
   `pos` does **not** depend on `fiscal` at all — it only emits an event.

2. **Trigger via the outbox, not a direct call.** `PosSaleService.post` emits a
   `FiscalizationRequested.v1` `DomainEvent` in the same TX as the sale insert. This
   is the textbook reason the outbox exists: fiscalization is a cross-module side
   effect that must survive a crash and must not be lost. `ApplicationEventPublisher`
   is explicitly not acceptable here. The poller hands the event to
   `FiscalizationServiceImpl`, which calls the active `FiscalProvider`.

3. **`FiscalProvider` SPI (the pluggability seam).**
   ```
   interface FiscalProvider {
     FiscalReceiptDto fiscalize(FiscalizableSaleDto sale);   // sign + submit one receipt
     ZReportResultDto closeDay(ZReportRequestDto req);       // daily Z
     boolean supports(String regimeCode);                    // "TZ-TRA-VFD"
   }
   ```
   Selected by an `orbix.fiscal.regime` config key (`TZ-TRA-VFD`, `NONE`, future
   `KE-ETIMS`, …). A `NoOpFiscalProvider` (`regime=NONE`) lets non-fiscal markets and
   dev run unchanged. This is the only place country logic lives.

4. **`FiscalReceipt` aggregate (new table `fiscal_receipt`).** One row per POS sale
   requiring fiscalization. Multi-tenant (`company_id` + `branch_id`, per CLAUDE.md),
   `extends UidEntity`. Holds: `pos_sale_id`, `status`
   (`PENDING|FISCALIZED|FAILED|EXEMPT`), `rctnum`, `gc`, `dc`/`znum`, `verification_code`,
   `verify_url`, `qr_payload`, `signature`, `submitted_at`, `efdms_response`,
   `attempt_count`, `last_error`. The `RCTNUM`/`GC`/`ZNUM` counters are TRA-mandated
   monotonic sequences kept **per fiscal device** with row-lock allocation — they are
   distinct from the `pos_sale.number` business sequence and MUST NOT be reused.
   `PosSale` gains denormalized read-only mirror fields (`fiscal_status`,
   `fiscal_verification_code`, `fiscal_qr_payload`) alongside the existing
   `fiscal_signature`, so the sync-pull and reprint paths don't cross the module
   boundary into `fiscal`.

5. **Two-phase receipt on the till.**
   - At sale time (online or offline) the POS prints a **PROVISIONAL** receipt — no QR,
     marked "fiscal receipt to follow". This is the cash document.
   - On the next `/sync` pull after fiscalization completes, the POS receives the
     fiscal artefacts and the receipt becomes reprintable as **FISCALIZED** with the
     TRA QR + verification code. (Whether a provisional-then-fiscal flow is itself TRA
     compliant for cash retail is the headline open question — see below.)

6. **Offline / deferred signing.** Because the POS sells offline, fiscalization
   naturally lags. The outbox already gives at-least-once delivery with retry/backoff
   and dead-lettering. EFDMS outages are handled by the same retry path; rows sit in
   `PENDING` until EFDMS is reachable. A monitored `FAILED`/`DEAD_LETTERED` queue
   surfaces receipts that never fiscalized — an accounting exception, not silent loss.

7. **Z report.** A scheduled job in `fiscal` (cron via existing `orbix.*` config style)
   calls `FiscalProvider.closeDay` per active device at end of business day, advancing
   `ZNUM`. Aligns with the existing POS end-of-day (`PosEodGuard`).

### Minimal pilot scope (smallest legally launch-viable slice)

Single branch, single registered VFD device, **TZS only**, cash + the existing tender
methods. For the pilot we ship:

- `fiscal` module + `FiscalProvider` SPI + `TraVfdFiscalProvider` (register, token,
  sign, post receipt, Z report) for **one device**.
- `fiscal_receipt` table + outbox-driven fiscalization + counter allocation.
- POS provisional/fiscal two-phase receipt with QR on reprint.
- `regime=NONE` NoOp for all non-TZ/dev environments.

Explicitly **deferred** to a follow-up (own ADR/stories if they grow):

- Multi-device / multi-branch device fleet management and device-failover.
- Credit notes / refunds fiscalization nuances and corrective receipts.
- Bulk re-fiscalization tooling and an ops console for the `FAILED` queue.
- A second-country provider (proves the SPI but not needed for pilot).

## Consequences

- **Positive.** Signing key never leaves the server; one regime seam to certify;
  fiscalization rides infrastructure we already trust (outbox + sync idempotency);
  POS stays offline-first; the `fiscal_signature` column finally has an owner; non-TZ
  and dev runs are unaffected via `regime=NONE`.
- **Negative / harder.** A sale is **not instantly fiscal** — there is a provisional
  window. If TRA does not accept provisional-then-deferred fiscal for cash retail, the
  pilot may need a connectivity guarantee at the till (a forced-online mode that blocks
  the sale until EFDMS responds), which weakens the offline-first promise. We must
  build counter-allocation carefully: a duplicated or skipped `RCTNUM`/`GC` is a
  compliance defect, so allocation is server-side with row locks, never client-side.
  A new module + table + outbox event-type + Flutter receipt states + Z-report job is
  real delivery cost before first sale.
- **Neutral.** Adds an external runtime dependency (EFDMS) to the daily close path;
  monitoring of the fiscalization queue becomes an operational duty. The OpenAPI
  contract gains fiscal status fields on the sync-pull payload (contract regen for
  TS + Dart clients).
- **Invariants held.** DB-agnostic (no native SQL; counters via Hibernate sequences +
  pessimistic lock), multi-tenant (`company_id`+`branch_id` on `fiscal_receipt`),
  uid/id duality (`fiscal_receipt extends UidEntity`), outbox for the cross-module
  trigger, `ModuleBoundaryTest` (pos→fiscal only via event; reads via mirrored fields).

## Alternatives considered

- **Sign on the POS client (Flutter holds the `.pfx`).** Rejected — ships the
  taxpayer's signing key to shop-floor hardware, and duplicates EFDMS connectivity +
  counter logic across every till; counter collisions become near-certain.
- **Physical EFD per till.** Rejected for the software-POS pilot — hardware cost,
  vendor lock-in, GPRS SIM per device, and a worse customer/cashier flow than a VFD;
  may still be a fallback if TRA disallows deferred VFD fiscalization for cash retail.
- **Synchronous fiscalization inside `PosSaleService.post` (block the sync push until
  EFDMS signs).** Rejected as the default — couples the sale transaction to an external
  HTTP call and breaks offline-first; kept in reserve as the "forced-online" mode if
  compliance demands instant fiscal receipts.
- **A standalone fiscalization microservice.** Rejected — contradicts
  [0001](0001-modular-monolith.md); a module behind the outbox gives the same isolation
  and can be lifted out later if a device fleet ever justifies it.
- **Third-party VFD-as-a-service gateway (e.g. a commercial VFD provider's API).**
  Not rejected — a legitimate `FiscalProvider` implementation. The SPI is designed so a
  hosted gateway can be dropped in if building the raw EFDMS XML/RSA path proves too
  costly to certify. Flagged as a fallback procurement decision, not the default build.

## Open questions requiring TRA / vendor confirmation

These gate the pilot and must be answered before fiscal receipt/print work is sized.
None of them changes the *shape* of the decision above; they tune scope and the
provisional-window risk.

1. **Is provisional-then-deferred fiscalization legal for cash retail?** Can a cash
   customer be handed a provisional receipt with the fiscal receipt/QR following on
   reconnect, or must the fiscal receipt be issued at the point of sale? This is the
   single biggest risk to offline-first. If "must be instant," we fall back to
   forced-online mode at the till.
2. **Exact EFDMS VFD API contract** — endpoints, the registration handshake, token
   lifetime, the receipt XML schema, signing algorithm and digest (SHA1 vs SHA256),
   and the precise `RCTNUM`/`GC`/`DC`/`ZNUM` semantics and rollover rules. Public
   sources are third-party reconstructions, not the TRA spec.
3. **Device/credential issuance** — what TRA issues per taxpayer vs per branch vs per
   till, and whether one VFD device can fiscalize multiple tills (drives whether the
   single-device pilot scope is even possible for one branch with several counters).
4. **Verification artefact format** — the exact `verify.tra.go.tz` URL/QR payload and
   the mandatory printed fields, so the receipt layout passes TRA inspection.
5. **Z-report timing and tolerance** — required submission window, behaviour if a day
   closes while EFDMS is unreachable, and how missed Z reports are reconciled.
6. **VRN/TIN capture rules** — when the buyer's TIN/VRN must be captured (B2B vs walk-in)
   and whether a missing buyer TIN blocks fiscalization for any sale class.
7. **Refunds / voids / corrective receipts** — the TRA mechanism for a fiscalized sale
   that is later voided or refunded (deferred from pilot, but confirm no pilot sale
   class needs it).
8. **Build-vs-buy for the adapter** — whether to implement raw EFDMS XML/RSA directly
   or integrate a certified commercial VFD gateway as the first `FiscalProvider`
   (affects the certification path and the open questions above).

## References

- [0001 — Modular monolith over microservices](0001-modular-monolith.md) (outbox, no microservices)
- ARCHITECTURE.md §2.10 (transactional outbox), §2.3 (DB-agnostic), §2.9/§5.2 (POS sync)
- DATA-MODEL.md §7.3, §17.12 (POS sale), §1.11 (`domain_event`)
- `orbix-engine-api/.../modules/pos/domain/entity/PosSale.java` (existing `fiscal_signature` hook)
- `orbix-engine-api/.../modules/pos/service/SyncServiceImpl.java` (sync push path)
- `orbix-engine-api/.../modules/common/domain/entity/DomainEvent.java` (outbox row)
- External (third-party, to be confirmed against TRA spec): TRA EFD/VFD suppliers
  <https://www.tra.go.tz/page/efd-vfd-suppliers>; VFD overview
  <https://tallysolutions.com/ssa/vat/vfd-tanzania/>; EFDMS/e-invoice overview
  <https://edicomgroup.com/blog/the-electronic-invoice-in-tanzania>; a VFD client
  integration showing the sign/RCTNUM/GC/Z flow
  <https://packagist.org/packages/eyecuejohn/tra-vfd-laravel>
