# TC-FISCAL — Fiscal Receipt Management

**Module:** fiscal  
**Stories:** US-POS-011, US-INT-001  
**API base:** `http://localhost:8081/api/v1`  
**Key types:** `FiscalRegime` (NONE, TZ_VFD), `FiscalStatus` (NONE, PENDING, PROVISIONAL, FISCALIZED, FAILED, EXEMPT)

---

### TC-FISCAL-001 — Default regime is NONE; sale gets fiscal_status=NONE

| Field | Value |
|-------|-------|
| **ID** | TC-FISCAL-001 |
| **Title** | With regime=NONE configured, POS sale fiscal_status is NONE; no EFDMS call made |
| **Area** | fiscal |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-POS-011 |
| **Preconditions** | 1. QA container configured with `orbix.fiscal.regime=NONE` (default). 2. Open session. 3. Item in stock. |
| **Steps** | 1. Post POS sale (TC-POS-CORE-004). 2. `GET /api/v1/pos-sales/uid/<uid>`. 3. Check `fiscal_status` field. |
| **Expected Result** | `data.fiscalStatus = "NONE"`; no `fiscal_receipt` row created; no outbound EFDMS call logged. |
| **Automatable?** | yes — unit test (`FiscalizationServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | `FiscalRegime.NONE` uses NoOpFiscalProvider |

---

### TC-FISCAL-002 — POS sale with regime=TZ_VFD transitions through PROVISIONAL to FISCALIZED

| Field | Value |
|-------|-------|
| **ID** | TC-FISCAL-002 |
| **Title** | With regime=TZ_VFD, sale starts PROVISIONAL; after outbox dispatch transitions to FISCALIZED |
| **Area** | fiscal |
| **Dimension** | INT |
| **Priority** | P1 |
| **Linked US-*** | US-POS-011 |
| **Preconditions** | 1. Deployment configured with `orbix.fiscal.regime=TZ_VFD`. 2. EFDMS test driver/stub is configured (not live TRA). 3. Open session. 4. Business day OPEN. |
| **Steps** | 1. Post POS sale. 2. Immediately check `pos_sale.fiscal_status` — expect PROVISIONAL. 3. Wait for outbox poll to fire and EFDMS stub to respond OK. 4. Re-check `fiscal_status`. |
| **Expected Result** | Step 2: `fiscal_status = "PROVISIONAL"`. Step 4: `fiscal_status = "FISCALIZED"`, `fiscal_receipt` row exists with `verification_code` and `gc_code` from stub. |
| **Automatable?** | partial — integration test with EFDMS stub; BLOCKED if TZ_VFD not configured in QA |
| **Result/Status** | |
| **Notes/IssueRef** | Requires EfdmsClient stub for CI. Live EFDMS only for UAT. |

---

### TC-FISCAL-003 — Outbox retry on EFDMS failure; max retries reach FAILED status

| Field | Value |
|-------|-------|
| **ID** | TC-FISCAL-003 |
| **Title** | If EFDMS call fails repeatedly, fiscal_status transitions to FAILED after max retries |
| **Area** | fiscal |
| **Dimension** | RELI |
| **Priority** | P1 |
| **Linked US-*** | US-POS-011 |
| **Preconditions** | regime=TZ_VFD with EFDMS stub configured to always fail (5xx). |
| **Steps** | 1. Post POS sale. 2. Wait for all outbox retry attempts to exhaust. 3. Check `fiscal_receipt.fiscal_status`. |
| **Expected Result** | `fiscal_status = "FAILED"`; dead-letter entry in outbox or equivalent log; supervisor is surfaced a failure alert. |
| **Automatable?** | partial — integration test with failure-mode stub; requires timer control |
| **Result/Status** | |
| **Notes/IssueRef** | FiscalStatus.FAILED: "max outbox retry attempts exhausted / dead-lettered" |

---

### TC-FISCAL-004 — Fiscalization event flows through outbox (domain event)

| Field | Value |
|-------|-------|
| **ID** | TC-FISCAL-004 |
| **Title** | FiscalizationRequested domain event is written to outbox in same TX as sale |
| **Area** | fiscal |
| **Dimension** | INT |
| **Priority** | P1 |
| **Linked US-*** | US-POS-011 |
| **Preconditions** | regime=TZ_VFD. Open session. |
| **Steps** | 1. Post POS sale. 2. Query `domain_event` table for `FiscalizationRequestedEventDto` event type. 3. Check event was written in same transaction as pos_sale row (same DB write timestamp). |
| **Expected Result** | `domain_event` row exists with `event_type = "FiscalizationRequested.v1"`, `aggregate_id = <sale_id>`; not dispatched yet (pending). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Outbox pattern: event written in same TX as business entity |

---

### TC-FISCAL-005 — fiscal_status denormalized on pos_sale matches fiscal_receipt

| Field | Value |
|-------|-------|
| **ID** | TC-FISCAL-005 |
| **Title** | pos_sale.fiscal_status stays in sync with fiscal_receipt.status after status transitions |
| **Area** | fiscal |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-POS-011 |
| **Preconditions** | regime=TZ_VFD. Sale posted → PROVISIONAL → FISCALIZED transition completed. |
| **Steps** | 1. Query `pos_sale.fiscal_status`. 2. Query `fiscal_receipt.fiscal_status` for same sale_id. |
| **Expected Result** | Both values are identical and = "FISCALIZED". Denormalization consistent per FiscalStatus comment: "mirrored denormalized into pos_sale.fiscal_status for reprint/sync-pull without crossing module boundaries". |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |
