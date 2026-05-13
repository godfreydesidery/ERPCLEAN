# Orbix ERP — User Stories

| Field | Value |
|---|---|
| Document | User Stories v0.1 (draft) |
| Author | Godfrey (with Claude) |
| Date | 2026-05-13 |
| Status | Draft — pending review |
| Companion | [PRD.md](PRD.md), [ARCHITECTURE.md](ARCHITECTURE.md), [DATA-MODEL.md](DATA-MODEL.md) |

User stories for the new system, organised by epic. Each epic maps to a PRD §5 module; each story maps to one or more entities in [DATA-MODEL.md](DATA-MODEL.md).

---

## How to read this document

**Story format.** Every story uses:

> **As a** [persona] **I want** [capability] **so that** [benefit].

**Story ID.** Prefix indicates the epic: `US-IAM-001`, `US-POS-014`, etc. IDs are stable — they persist across renames and can be cited in commits, PRs, tests, and tickets.

**Priority.**
- **P1** — MVP. Required to launch the pilot branch (PRD §10).
- **P2** — Hardening / breadth. Phase 2.
- **P3** — Group, integrations, intelligence. Phase 3.

**Size** (rough engineering estimate).
- **S** — ≤ 2 days
- **M** — 3-5 days
- **L** — 1-2 weeks
- **XL** — 2+ weeks (decompose before sprint)

**Surface** — which client(s) host the UI: `Web`, `POS`, `WMS`, `Backend` (admin / API only), `All`.

**Acceptance criteria** are written Given / When / Then. They are the contract for "done"; they are not exhaustive UX detail.

**Personas** are defined in [PRD §3](PRD.md). Common shorthands:
| Shorthand | Persona |
|---|---|
| `Cashier` | Cashier / Till Operator |
| `Supervisor` | Floor Supervisor |
| `Storekeeper` | Storekeeper / Inventory Clerk |
| `Merchandiser` | Merchandiser / Buyer |
| `Salesperson` | Salesperson (Counter) |
| `Agent` | Field Sales Agent |
| `Operator` | Production Operator |
| `Accountant` | Accountant / Finance |
| `Manager` | Branch Manager |
| `Admin` | System Administrator |
| `HQ` | HQ / Group Owner |

---

## Epic index

| Epic | Code | Module(s) | Stories |
|---|---|---|---|
| 1. Identity, Access & Audit | `IAM` | PRD §5.1 | 14 |
| 2. Company, Branch & Reference Data | `COMP` | PRD §5.2 | 9 |
| 3. Catalog (Items, Pricing, Promotions) | `CAT` | PRD §5.3 | 14 |
| 4. Stock & Movements | `STOCK` | PRD §5.4 | 10 |
| 5. Procurement | `PROC` | PRD §5.5 | 12 |
| 6. Sales (Counter & Back-office) | `SALES` | PRD §5.6 | 13 |
| 7. Point of Sale | `POS` | PRD §5.7 | 18 |
| 8. WMS (Field Sales) | `WMS` | PRD §5.8 | 12 |
| 9. Debt Management | `DEBT` | PRD §5.9 | 7 |
| 10. Production | `PROD` | PRD §5.10 | 8 |
| 11. Day Management | `DAY` | PRD §5.11 | 5 |
| 12. HR (light) | `HR` | PRD §5.12 | 5 |
| 13. Reporting | `RPT` | PRD §5.13 | 10 |
| 14. Integrations | `INT` | PRD §5.14 | 5 |
| 15. Platform (cross-cutting) | `PLAT` | Cross | 12 |
| 16. AI / LLM | `AI` | PRD §10 P3 | 5 |

---

# Epic 1: Identity, Access & Audit (`IAM`)

Establishes who can do what. Without this epic, nothing else is safe.

### US-IAM-001 — Log in with username and password
**As** any user **I want** to log in with a username and password **so that** I can use the system.
**P:** P1 · **Size:** M · **Surface:** All
**AC:**
- Given a valid username and password, when I submit, then I receive an access JWT (≤ 15 min) and a refresh token (≤ 30 days, single-use, rotated on next use).
- Given the wrong password, when I submit, then `failed_login_count` increments and I see a generic "invalid credentials" message — never "no such user".
- After 5 consecutive failed attempts on the same username, the account is locked until `locked_until`, decayed exponentially.
- The login event is recorded in `audit_log` with action `LOGIN`, source IP, and client type.

### US-IAM-002 — Log out
**As** any user **I want** to log out **so that** my session is no longer valid.
**P:** P1 · **Size:** S · **Surface:** All
**AC:**
- On logout, the current refresh token is deleted from `refresh_token`; the access JWT's JTI is added to the Redis blacklist until natural expiry.
- A `LOGOUT` row is written to `audit_log`.

### US-IAM-003 — Log out everywhere
**As** any user **I want** to revoke all of my active sessions **so that** I can recover from a lost device.
**P:** P2 · **Size:** S · **Surface:** Web
**AC:**
- "Log me out everywhere" deletes all of my `refresh_token` rows; access JWTs are added to the blacklist.
- I am presented with a list of active sessions (device, last activity) before confirming.

### US-IAM-004 — Reset forgotten password
**As** any user with a verified email **I want** to reset my password **so that** I can regain access without contacting an admin.
**P:** P2 · **Size:** M · **Surface:** Web
**AC:**
- A reset link, signed and single-use, is emailed to the verified address. Link expires in 30 minutes.
- The reset writes a new bcrypt hash with cost ≥ 12 and revokes existing refresh tokens.
- Audit log records the reset; the user is notified by email that their password changed.

### US-IAM-005 — Change my password
**As** any user **I want** to change my password from inside the app **so that** I can rotate it on a schedule.
**P:** P1 · **Size:** S · **Surface:** Web
**AC:**
- Requires re-entry of current password.
- New password fails if it is the same as current, fewer than 10 characters, or in the common-leaks list.
- Refresh tokens for other sessions are revoked (current session continues).

### US-IAM-006 — Create a user
**As** Admin **I want** to create a user **so that** new staff can log in.
**P:** P1 · **Size:** S · **Surface:** Web · **Entities:** `app_user`
**AC:**
- Required: username (unique, lowercase ASCII), display name, default company.
- A temporary password is generated; the user is forced to change it on first login.
- Creation is audited; the actor's identity is recorded.

### US-IAM-007 — Deactivate a user
**As** Admin **I want** to deactivate a user **so that** they can no longer log in.
**P:** P1 · **Size:** S · **Surface:** Web
**AC:**
- Setting `app_user.status = INACTIVE` immediately invalidates active sessions on next API call.
- The user row is preserved; their historical actions remain in audit and references.
- Reactivation is allowed.

### US-IAM-008 — Define a role with privileges
**As** Admin **I want** to define a role as a set of privileges **so that** I can assign permissions in bulk.
**P:** P1 · **Size:** M · **Surface:** Web · **Entities:** `role`, `privilege`, `role_privilege`
**AC:**
- I can create a role with a unique code (e.g. `BRANCH_MANAGER`), display name, and selection of privileges.
- System roles cannot be deleted or have their core privileges removed.
- Changes to role-privilege mappings are audited.

### US-IAM-009 — Assign a role to a user, scoped per company / branch
**As** Admin **I want** to assign a role to a user, scoped to a company and optionally a branch **so that** I can match permission to context.
**P:** P1 · **Size:** M · **Surface:** Web · **Entities:** `user_role`
**AC:**
- Same user can hold different roles in different companies.
- An assignment can be branch-restricted (NULL `branch_id` = all branches in that company).
- Assignments record `granted_by` and `granted_at`; revocations set `revoked_at` (no row deletion).

### US-IAM-010 — Authorise a privileged action with a supervisor PIN
**As** Cashier **I want** to request supervisor authorisation in-line **so that** I can complete restricted actions (void, over-discount, oversell) without leaving my till.
**P:** P1 · **Size:** M · **Surface:** POS, Web
**AC:**
- The protected action triggers a PIN modal; a user with the required privilege types their username + PIN/password.
- On success, a short-lived (60-second) authorisation token scoped to that single action is issued; the original action retries with the token attached.
- The supervisor's identity is recorded on the affected entity (`supervisor_id`) and in audit.
- A failed authorisation does not increment the cashier's `failed_login_count` — only the supervisor's.

### US-IAM-011 — Enrol a biometric
**As** Supervisor **I want** to enrol a cashier's fingerprint **so that** they can sign in to POS quickly.
**P:** P2 · **Size:** M · **Surface:** POS · **Entities:** `biometric_enrolment`
**AC:**
- Enrolment captures the template, encrypts with AES-GCM using a per-deployment key, stores ciphertext only.
- Raw images and unencrypted templates are never persisted.
- Enrolment requires Supervisor presence (Supervisor authenticates first).

### US-IAM-012 — Log in with a fingerprint
**As** Cashier **I want** to log in to POS with my fingerprint **so that** I can be at the till in under 5 seconds.
**P:** P2 · **Size:** M · **Surface:** POS
**AC:**
- Fingerprint match returns the matched user; backend issues the same JWT pair as a password login.
- If no match within 3 attempts, the device falls back to username/password.
- A `LOGIN` audit row records `method = FINGERPRINT`.

### US-IAM-013 — View the audit log
**As** Admin or Manager **I want** to filter and view the audit log **so that** I can investigate incidents.
**P:** P1 · **Size:** M · **Surface:** Web · **Entities:** `audit_log`
**AC:**
- Filters: actor, entity type, entity id, action, date range, branch.
- Default sort: newest first.
- The list paginates; full row detail (before/after JSON) appears in a side panel.
- Export of the filtered set to CSV requires `AUDIT.EXPORT` privilege.

### US-IAM-014 — Verify audit-log integrity
**As** Admin **I want** to verify the audit log has not been tampered with **so that** I can trust it during compliance review.
**P:** P2 · **Size:** S · **Surface:** Web
**AC:**
- An "Integrity check" runs over a chosen date range, recomputing `row_hash` from content and previous hash.
- On success, the screen shows "OK · N rows verified". On failure, the first broken row is highlighted.

---

# Epic 2: Company, Branch & Reference Data (`COMP`)

Configuration that other modules depend on. Mostly done once during onboarding.

### US-COMP-001 — Set up the organisation and first company
**As** Admin (first-run) **I want** to define the organisation and at least one company **so that** transactions have a legal owner.
**P:** P1 · **Size:** M · **Surface:** Web · **Entities:** `organisation`, `company`
**AC:**
- First-run wizard asks for organisation name, currency, country; then company code, name, TIN, VRN, time zone.
- On completion, the wizard creates a default branch, a `VAT_STD` group, a default `RETAIL` price list, an `EA` UoM, a walk-in customer, and standard roles.

### US-COMP-002 — Edit company profile
**As** Admin **I want** to edit company contact, tax, and bank details **so that** invoices show correct information.
**P:** P1 · **Size:** S · **Surface:** Web
**AC:**
- All edits are audited.
- Logo upload accepts PNG/JPG ≤ 2 MB and stores in object storage; `logo_object_key` is set.

### US-COMP-003 — Configure default invoice and quotation notes
**As** Admin **I want** to set default footer text on invoices and quotations **so that** legal / payment terms appear automatically.
**P:** P1 · **Size:** S · **Surface:** Web

### US-COMP-004 — Create and edit branches
**As** Admin **I want** to create a branch **so that** stock and tills can be assigned to it.
**P:** P1 · **Size:** S · **Surface:** Web · **Entities:** `branch`
**AC:**
- Branch code is unique within company. Type, time zone, and physical address are captured.
- Setting `is_default = true` automatically clears the flag on the previous default branch in the same company.

### US-COMP-005 — Set the active branch context
**As** any user with access to multiple branches **I want** to switch my active branch **so that** my screens scope correctly.
**P:** P1 · **Size:** S · **Surface:** Web · **Entities:** —
**AC:**
- The active branch is sent on every API call as `X-Branch-Id`.
- A change of branch updates dashboards, search results, and default-form values immediately.

### US-COMP-006 — Manage VAT groups
**As** Admin or Accountant **I want** to manage VAT groups including effective-from dates **so that** historical invoices stay correct when the rate changes.
**P:** P1 · **Size:** M · **Surface:** Web · **Entities:** `vat_group`
**AC:**
- A new rate is captured with `valid_from`; previous rate remains for historical lookups.
- One VAT group is `is_default` per company.

### US-COMP-007 — Manage units of measure and conversions
**As** Admin **I want** to define UoMs and conversions **so that** items can be sold in different packs.
**P:** P1 · **Size:** S · **Surface:** Web · **Entities:** `uom`, `uom_conversion`

### US-COMP-008 — Configure number sequences per branch
**As** Admin **I want** to configure document numbering per branch **so that** numbers are predictable and unique.
**P:** P1 · **Size:** S · **Surface:** Web · **Entities:** `number_sequence`
**AC:**
- Per (company, branch, doc_type) I can set prefix and pad width.
- `current_value` is editable only by users with `NUMBER_SEQUENCE.EDIT` and is audited.

### US-COMP-009 — Create additional companies under the organisation
**As** HQ **I want** to create additional companies **so that** the group can operate multiple legal entities.
**P:** P3 · **Size:** M · **Surface:** Web

---

# Epic 3: Catalog (`CAT`)

Items, hierarchies, pricing, promotions. The catalog is the most-queried data in the system.

### US-CAT-001 — Create an item
**As** Storekeeper or Merchandiser **I want** to create an item **so that** it can be transacted on.
**P:** P1 · **Size:** M · **Surface:** Web · **Entities:** `item`
**AC:**
- Required: code (unique per company), name, type, item group, UoM, VAT group.
- Default `is_tracked = true`.
- Creation indexes the item in Meilisearch within 1 second.

### US-CAT-002 — Edit an item
**As** Storekeeper **I want** to edit an item's attributes **so that** the master stays current.
**P:** P1 · **Size:** S · **Surface:** Web
**AC:**
- Changing `code` is allowed only if no transactions reference it (otherwise add an alias barcode instead).
- All edits are audited; price-affecting changes propagate to the search index.

### US-CAT-003 — Add a barcode to an item
**As** Storekeeper **I want** to add additional barcodes (single, pack, case) to an item **so that** scanning works for any unit.
**P:** P1 · **Size:** S · **Surface:** Web · **Entities:** `item_barcode`
**AC:**
- Barcode is unique per company.
- Each barcode declares its pack UoM and quantity.

### US-CAT-004 — Manage the item-group hierarchy
**As** Merchandiser **I want** to manage the item-group tree **so that** reporting and navigation reflect categories.
**P:** P1 · **Size:** M · **Surface:** Web · **Entities:** `item_group`
**AC:**
- A group can be moved to a different parent; descendants follow.
- A group with items cannot be deleted; it can be archived.

### US-CAT-005 — Search for an item
**As** any user **I want** to type a partial name, code, or barcode and get matching items **so that** I can find items fast.
**P:** P1 · **Size:** M · **Surface:** All · **Entities:** Meilisearch `items` index
**AC:**
- Search returns within 100 ms for queries up to 32 characters on a 50k-item catalog.
- Results are scoped to my active company.
- Typo tolerance: "colla" returns "Cola" within the top 5 results.

### US-CAT-006 — Maintain an item-supplier mapping
**As** Merchandiser **I want** to map suppliers to items including their codes and last buy price **so that** LPOs default correctly.
**P:** P1 · **Size:** S · **Surface:** Web · **Entities:** `item_supplier`

### US-CAT-007 — Create and maintain a price list
**As** Merchandiser **I want** to create price lists (retail, wholesale, agent) **so that** different customers get different prices.
**P:** P1 · **Size:** M · **Surface:** Web · **Entities:** `price_list`, `price_list_item`
**AC:**
- Setting a new price closes the previous `price_list_item` with `valid_to` and opens a new one.
- The change is recorded in `price_change_log`.

### US-CAT-008 — Set a customer's price tier
**As** Salesperson **I want** to assign a customer to a price list **so that** quotations and invoices pick the right prices.
**P:** P1 · **Size:** S · **Surface:** Web

### US-CAT-009 — Bulk-edit items in a grid
**As** Merchandiser **I want** to edit names, prices, reorder points across many items in a grid **so that** I am not clicking through one item at a time.
**P:** P2 · **Size:** L · **Surface:** Web
**AC:**
- I can filter, edit cells inline, see pending edits highlighted, then "Save all" in one transaction.
- Failures roll back the batch with per-row error messages.

### US-CAT-010 — Bulk-import items from CSV
**As** Merchandiser **I want** to upload a CSV of new or updated items **so that** I can onboard a supplier's catalogue quickly.
**P:** P2 · **Size:** L · **Surface:** Web
**AC:**
- File is validated row-by-row; a pre-import preview shows which rows will create vs update vs fail.
- The import runs as a background job; result email lists successes and failures.

### US-CAT-011 — Configure reorder points per item per branch
**As** Storekeeper **I want** to set reorder min/max per item per branch **so that** the system can flag low stock.
**P:** P1 · **Size:** S · **Surface:** Web · **Entities:** `item_branch_balance`

### US-CAT-012 — Create a promotion
**As** Merchandiser **I want** to create a promotion (percent-off, BOGO, bundle, step-quantity) **so that** items can be discounted automatically.
**P:** P2 · **Size:** L · **Surface:** Web · **Entities:** `promotion`, `promotion_item`
**AC:**
- Required: code, type, params, start, end, priority.
- Promotions are scoped to a price list optionally.
- POS / Web sales apply the highest-priority active promotion at line evaluation.

### US-CAT-013 — Manage offline catalogue on POS / WMS
**As** Cashier or Agent **I want** my device to keep an up-to-date catalogue **so that** I can find items offline.
**P:** P1 · **Size:** L · **Surface:** POS, WMS
**AC:**
- On login, the device pulls a delta of items, prices, and promotions changed since its `lastCursor`.
- Items inactive for > 90 days at this branch are not synced (footprint control).

### US-CAT-014 — Audit price changes over time
**As** Manager or Accountant **I want** to see who changed which price when **so that** I can answer pricing disputes.
**P:** P1 · **Size:** S · **Surface:** Web · **Entities:** `price_change_log`

---

# Epic 4: Stock & Movements (`STOCK`)

The truth about what we own, where, and what it cost. All balances derive from the `stock_move` ledger.

### US-STOCK-001 — View stock on hand for an item
**As** Storekeeper or Salesperson **I want** to view stock on hand for an item across branches **so that** I can answer customer or planning questions.
**P:** P1 · **Size:** S · **Surface:** Web · **Entities:** `item_branch_balance`
**AC:**
- Shows per-branch `qty_on_hand`, `qty_reserved`, `qty_in_transit`, `last_moved_at`.
- Drill-down opens the stock card (US-STOCK-002).

### US-STOCK-002 — View stock card (movement history)
**As** Storekeeper **I want** to see every movement for an item at a branch **so that** I can trace variances.
**P:** P1 · **Size:** M · **Surface:** Web · **Entities:** `stock_move`
**AC:**
- Filters: date range, move type. Sort: newest first.
- Each row links to the source document (GRN, sale, transfer).
- Running balance column.

### US-STOCK-003 — Adjust stock with a reason
**As** Storekeeper **I want** to record a stock adjustment **so that** physical reality is reflected.
**P:** P1 · **Size:** M · **Surface:** Web
**AC:**
- Requires reason; requires `STOCK.ADJUST` privilege; large adjustments (configurable threshold) require supervisor authorisation.
- Posts an `ADJUSTMENT` move.

### US-STOCK-004 — Start a stock count
**As** Storekeeper **I want** to start a stock-count session for a branch **so that** the system can compare against physical.
**P:** P2 · **Size:** M · **Surface:** Web · **Entities:** `stock_count`, `stock_count_line`
**AC:**
- I select a branch and a type (full, cycle, spot). System snapshots `system_qty` per item at start.
- Status moves to `IN_PROGRESS`.

### US-STOCK-005 — Capture counted quantities
**As** Storekeeper **I want** to enter counted quantities **so that** the system can compute variances.
**P:** P2 · **Size:** M · **Surface:** Web, POS (offline-friendly)
**AC:**
- Items can be scanned or searched.
- Counts can be entered in waves; the session remains `IN_PROGRESS` until closed.

### US-STOCK-006 — Close a stock count and post variances
**As** Manager **I want** to close a stock count **so that** variances are posted as adjustments and the cache is reconciled.
**P:** P2 · **Size:** M · **Surface:** Web
**AC:**
- Each item with a variance produces an `ADJUSTMENT` `stock_move` for the difference.
- Variances above a configurable threshold per item require an explicit reason.
- The session status moves to `POSTED`.

### US-STOCK-007 — Issue an inter-branch transfer
**As** Storekeeper at the issuing branch **I want** to issue a transfer to another branch **so that** stock moves between locations with traceability.
**P:** P3 · **Size:** M · **Surface:** Web · **Entities:** `stock_transfer`, `stock_transfer_line`
**AC:**
- On issue, the system posts `TRANSFER_OUT` and increments `qty_in_transit` at the destination branch's balance row.

### US-STOCK-008 — Receive an inter-branch transfer
**As** Storekeeper at the receiving branch **I want** to receive a transfer with confirmed quantities **so that** variances are flagged.
**P:** P3 · **Size:** M · **Surface:** Web
**AC:**
- On receive, posts `TRANSFER_IN`; clears `qty_in_transit`.
- Variance (received ≠ issued) requires a reason and is reported.

### US-STOCK-009 — See alerts for low or negative stock
**As** Storekeeper **I want** alerts for items below reorder minimum or in negative **so that** I can act fast.
**P:** P1 · **Size:** M · **Surface:** Web
**AC:**
- A "Stock alerts" tile on the dashboard lists items where `qty_on_hand < reorder_min` or `qty_on_hand < 0`.
- Clicking an alert opens the item / branch detail.

### US-STOCK-010 — Block oversell unless authorised
**As** the system **I want** to block sales of items with insufficient stock unless `STOCK.OVERSELL` is granted **so that** stock integrity is preserved.
**P:** P1 · **Size:** M · **Surface:** All
**AC:**
- POS and Web invoice posting check stock at the line; failure shows a clear message and offers supervisor authorisation if the privilege exists.
- Authorised oversells are recorded with supervisor identity.

---

# Epic 5: Procurement (`PROC`)

### US-PROC-001 — Create a supplier
**As** Merchandiser **I want** to create a supplier **so that** LPOs can be raised against them.
**P:** P1 · **Size:** S · **Surface:** Web · **Entities:** `party`, `supplier`

### US-PROC-002 — Raise an LPO
**As** Merchandiser **I want** to raise an LPO **so that** a supplier knows what we will buy.
**P:** P1 · **Size:** L · **Surface:** Web · **Entities:** `lpo_order`, `lpo_order_line`
**AC:**
- Lines default to the supplier's last buy price for each item if present.
- Subtotal, tax, and total are computed live.
- Initial status: `DRAFT`. I can save as draft or submit.

### US-PROC-003 — Approve or reject an LPO
**As** Manager **I want** to approve LPOs above a value threshold **so that** large purchases have oversight.
**P:** P1 · **Size:** M · **Surface:** Web
**AC:**
- LPOs below the configured threshold auto-approve.
- Approval requires `LPO.APPROVE`; rejection requires a reason.
- Approved LPO becomes immutable except for cancellation.

### US-PROC-004 — Receive against an LPO (GRN)
**As** Storekeeper **I want** to receive goods against an LPO **so that** stock and supplier debt are updated.
**P:** P1 · **Size:** L · **Surface:** Web · **Entities:** `grn`, `grn_line`
**AC:**
- Lines default to LPO ordered minus already-received quantities.
- Partial receipt is allowed; LPO transitions to `PARTIALLY_RECEIVED` or `RECEIVED`.
- On post: `GRN` `stock_move` per line, `item_branch_balance.avg_cost` and `last_cost` updated; `debt_entry` opened for the supplier.

### US-PROC-005 — Receive without an LPO (direct GRN)
**As** Storekeeper with `GRN.DIRECT` **I want** to record a receipt with no LPO **so that** ad-hoc deliveries can be captured.
**P:** P2 · **Size:** M · **Surface:** Web

### US-PROC-006 — Match a supplier invoice to one or more GRNs
**As** Accountant **I want** to record the supplier's invoice against the GRN(s) it covers **so that** three-way match is enforced.
**P:** P1 · **Size:** L · **Surface:** Web · **Entities:** `supplier_invoice`, `supplier_invoice_grn`
**AC:**
- The system warns if invoice total differs from GRN total beyond a configurable tolerance.
- On post: `debt_entry` for the supplier closes the GRN-opened debt and opens an invoice-bounded one with `due_date`.

### US-PROC-007 — Pay a supplier
**As** Accountant **I want** to record a supplier payment and allocate it against invoices **so that** payables are reduced accurately.
**P:** P1 · **Size:** M · **Surface:** Web · **Entities:** `supplier_payment`, `supplier_payment_allocation`

### US-PROC-008 — Return goods to a vendor
**As** Storekeeper **I want** to return goods to the supplier **so that** damaged or wrong items leave my stock.
**P:** P2 · **Size:** M · **Surface:** Web · **Entities:** `vendor_return`

### US-PROC-009 — Receive and allocate a vendor credit note
**As** Accountant **I want** to record a vendor credit note **so that** my payable reduces.
**P:** P2 · **Size:** M · **Surface:** Web · **Entities:** `vendor_credit_note`

### US-PROC-010 — Run a purchase quotation
**As** Merchandiser **I want** to send the same RFQ to multiple suppliers **so that** I can compare prices.
**P:** P2 · **Size:** M · **Surface:** Web · **Entities:** `purchase_quotation`

### US-PROC-011 — Print or email an LPO
**As** Merchandiser **I want** to send an LPO as PDF **so that** the supplier has a copy.
**P:** P1 · **Size:** S · **Surface:** Web

### US-PROC-012 — Cancel an LPO or GRN
**As** Merchandiser or Manager **I want** to cancel an LPO or GRN **so that** errors can be removed.
**P:** P1 · **Size:** M · **Surface:** Web
**AC:**
- Cancelling a posted GRN issues compensating `stock_move`s and `debt_entry`s (never deletes).
- Requires `GRN.CANCEL` and a reason. Audited.

---

# Epic 6: Sales (`SALES`)

Counter-driven sales, debt-bearing invoices, receipts, returns. Distinct from POS (Epic 7) which is offline-first.

### US-SALES-001 — Create a customer
**As** Salesperson **I want** to create a customer **so that** I can invoice them.
**P:** P1 · **Size:** S · **Surface:** Web · **Entities:** `party`, `customer`
**AC:**
- Required: name, category. Optional: TIN, phone, addresses, credit limit, price list, default agent.

### US-SALES-002 — Edit a customer
**As** Salesperson **I want** to update a customer's details **so that** invoicing and statements remain correct.
**P:** P1 · **Size:** S · **Surface:** Web

### US-SALES-003 — Raise a sales quotation
**As** Salesperson **I want** to raise a quotation **so that** the customer has a written price offer.
**P:** P2 · **Size:** M · **Surface:** Web · **Entities:** `sales_quotation`

### US-SALES-004 — Convert a quotation to an invoice
**As** Salesperson **I want** to convert an accepted quotation to an invoice in one click **so that** I do not re-key lines.
**P:** P2 · **Size:** S · **Surface:** Web

### US-SALES-005 — Raise a sales invoice
**As** Salesperson **I want** to raise a sales invoice **so that** the sale is recorded.
**P:** P1 · **Size:** L · **Surface:** Web · **Entities:** `sales_invoice`, `sales_invoice_line`
**AC:**
- Customer's `price_list_id` resolves unit prices automatically.
- On post: stock movements, debt entry (if credit) or cash entry (if cash), domain event `SalesInvoicePosted.v1`.
- Credit-limit check blocks credit sales above customer's `credit_limit_amount` unless `SALES_INVOICE.OVERRIDE_CREDIT` is held.

### US-SALES-006 — Apply a discount (line or header)
**As** Salesperson **I want** to apply discounts at line or header level **so that** I can match negotiated deals.
**P:** P1 · **Size:** M · **Surface:** Web
**AC:**
- Discount above a configurable percent requires supervisor authorisation.
- Unit price after discount cannot drop below `item.min_sell_price` without override.

### US-SALES-007 — Void a posted sales invoice
**As** Manager **I want** to void a posted invoice **so that** errors can be reversed.
**P:** P1 · **Size:** M · **Surface:** Web
**AC:**
- Voiding issues compensating `stock_move` and `debt_entry`/`cash_entry` rows.
- Status moves to `VOIDED`; original document is preserved. Reason mandatory.

### US-SALES-008 — Capture a sales receipt
**As** Salesperson **I want** to capture a receipt from a customer **so that** their debt reduces.
**P:** P1 · **Size:** M · **Surface:** Web · **Entities:** `sales_receipt`
**AC:**
- Method, reference, amount, currency captured.
- Posting creates a `cash_entry` and reduces customer's outstanding debt total.

### US-SALES-009 — Allocate a receipt across invoices
**As** Accountant **I want** to allocate a receipt amount across one or more open invoices **so that** ageing is correct.
**P:** P1 · **Size:** L · **Surface:** Web · **Entities:** `receipt_allocation`
**AC:**
- The screen shows the customer's open invoices oldest-first by default.
- I can enter the amount to apply per invoice; the system enforces totals.
- Unallocated remainder is held as customer credit (visible on next receipt allocation).

### US-SALES-010 — Process a customer return
**As** Salesperson with `CUSTOMER_RETURN.POST` **I want** to record returned goods **so that** stock or damage is captured and a credit note opens.
**P:** P2 · **Size:** L · **Surface:** Web
**AC:**
- I select the original invoice (where known); lines default to it.
- `restock = true` posts `RETURN_IN`; `false` posts `DAMAGE`.

### US-SALES-011 — Apply a customer credit note to an invoice
**As** Salesperson **I want** to apply a credit note to an open invoice **so that** the customer's debt is reduced without a cash receipt.
**P:** P2 · **Size:** M · **Surface:** Web

### US-SALES-012 — Build and dispatch a packing list
**As** Salesperson or Storekeeper **I want** to create a packing list for an invoice **so that** physical delivery is tracked.
**P:** P2 · **Size:** M · **Surface:** Web · **Entities:** `packing_list`

### US-SALES-013 — Reprint a sales document (invoice / receipt) with audit
**As** Salesperson **I want** to reprint a document **so that** I can give the customer another copy.
**P:** P1 · **Size:** S · **Surface:** Web
**AC:**
- The reprint marks the copy number ("Copy 2 of original") and is recorded in audit.

---

# Epic 7: Point of Sale (`POS`)

Offline-first, hardware-aware. The till must never stop selling.

### US-POS-001 — Log in to POS (password)
**As** Cashier **I want** to log in to POS with my username and password **so that** my shift can begin.
**P:** P1 · **Size:** M · **Surface:** POS
**AC:**
- Same JWT mechanism as Web (US-IAM-001).
- After 30 days of no successful login, the local credential cache is purged and the device requires online login.

### US-POS-002 — Open a till session
**As** Cashier **I want** to open a till session with my opening float **so that** sales can be taken on this till.
**P:** P1 · **Size:** M · **Surface:** POS · **Entities:** `till_session`
**AC:**
- Requires an `OPEN` `business_day` for the branch.
- Blocks if the till already has an `OPEN` session held by someone else.
- `opening_float_amount` is required (can be zero).

### US-POS-003 — Add an item to the cart by scanning a barcode
**As** Cashier **I want** to add an item by scanning its barcode **so that** I can complete sales quickly.
**P:** P1 · **Size:** M · **Surface:** POS
**AC:**
- Scan resolves to an `item` + `pack_qty`; cart line is added or quantity incremented.
- Unknown barcode shows a clear "not found" toast, with option to search by name.

### US-POS-004 — Add an item to the cart by typeahead search
**As** Cashier **I want** to type a partial name or code and pick the item **so that** items without barcodes can be sold.
**P:** P1 · **Size:** M · **Surface:** POS
**AC:**
- Local index returns under 80 ms.
- A keyboard shortcut focuses the search box from anywhere in the cart screen.

### US-POS-005 — Apply a line or header discount
**As** Cashier **I want** to apply a discount on a line or the whole cart **so that** I can match a promised price.
**P:** P1 · **Size:** M · **Surface:** POS
**AC:**
- Discount above the configured threshold triggers a supervisor PIN modal (US-IAM-010).
- Discount cannot push price below `item.min_sell_price` without override.

### US-POS-006 — Void a line in the cart
**As** Cashier **I want** to remove an unwanted line **so that** I can correct mistakes.
**P:** P1 · **Size:** S · **Surface:** POS
**AC:**
- Lines added in the current cart can be removed freely before tendering.
- Removing a line after partial tender is a supervisor action.

### US-POS-007 — Hold the current cart
**As** Cashier **I want** to hold an unfinished cart **so that** I can serve the next customer and resume later.
**P:** P1 · **Size:** M · **Surface:** POS
**AC:**
- Holding tags the cart with a 4-digit code and the cashier's name.
- Held carts are local to the till and recoverable across app restarts.

### US-POS-008 — Recall a held cart
**As** Cashier **I want** to recall a held cart **so that** I can finish a paused sale.
**P:** P1 · **Size:** S · **Surface:** POS

### US-POS-009 — Take a mixed-tender payment
**As** Cashier **I want** to accept cash + card + mobile money in one sale **so that** customers can pay flexibly.
**P:** P1 · **Size:** L · **Surface:** POS · **Entities:** `pos_payment`
**AC:**
- Each tender method is added incrementally; running balance updates.
- When tender ≥ total, the sale is completed and change is computed.
- Card and mobile money tenders capture a reference; PAN is never stored.

### US-POS-010 — Print a receipt
**As** Cashier **I want** the receipt to print automatically after tendering **so that** the customer leaves with a copy.
**P:** P1 · **Size:** M · **Surface:** POS
**AC:**
- Receipt template is configurable per company.
- Printer offline triggers a banner; the sale completes regardless and is queued for reprint.

### US-POS-011 — Sign a receipt via fiscal printer (where applicable)
**As** Cashier in a fiscal-regulated region **I want** the receipt to be fiscally signed **so that** the sale is compliant.
**P:** P2 · **Size:** L · **Surface:** POS
**AC:**
- The fiscal driver is selected by deployment configuration.
- Signature returned by the device is stored on `pos_sale.fiscal_signature`.
- Driver failure blocks the sale only if the region requires it; otherwise queues for retry.

### US-POS-012 — Reprint the last receipt
**As** Cashier **I want** to reprint the previous receipt **so that** I can recover from a printer jam.
**P:** P1 · **Size:** S · **Surface:** POS

### US-POS-013 — Record a cash pickup
**As** Supervisor **I want** to record a cash pickup from the till **so that** the close reconciles.
**P:** P1 · **Size:** S · **Surface:** POS · **Entities:** `cash_pickup`
**AC:**
- Requires supervisor authentication on the till.
- Decrements expected drawer cash.

### US-POS-014 — Pay petty cash from the till
**As** Supervisor **I want** to record a petty-cash payout **so that** the disbursement is tracked.
**P:** P1 · **Size:** S · **Surface:** POS · **Entities:** `petty_cash`

### US-POS-015 — View an X-report mid-shift
**As** Cashier or Supervisor **I want** to see an X-report without closing the till **so that** I can spot-check during the shift.
**P:** P2 · **Size:** S · **Surface:** POS

### US-POS-016 — Close a till session
**As** Cashier **I want** to close my till with a declared cash count **so that** the shift ends.
**P:** P1 · **Size:** L · **Surface:** POS
**AC:**
- The system computes `expected_cash_amount` from float + sales by cash + receipts − pickups − petty cash.
- I enter `declared_cash_amount`; variance is computed.
- Variance above a configurable threshold requires supervisor authorisation.
- A Z-report PDF is generated and stored in object storage.

### US-POS-017 — Sell while offline
**As** Cashier **I want** to keep selling when the network is down **so that** customers are not blocked.
**P:** P1 · **Size:** XL · **Surface:** POS
**AC:**
- All sales, payments, pickups, petty cash, and close-till functions work without network.
- Items, prices, and held carts are sourced from local SQLite.
- A persistent banner shows offline state and queue depth.

### US-POS-018 — Sync queued operations on reconnect
**As** the POS device **I want** to push queued ops to the backend automatically **so that** the server catches up.
**P:** P1 · **Size:** L · **Surface:** POS · **Entities:** `client_op`
**AC:**
- Ops push in FIFO order with `clientOpId` for idempotency.
- A failed op is retried with exponential backoff; permanent failures are surfaced to the supervisor.
- The UI shows queue depth and last-sync time.

---

# Epic 8: WMS — Field Sales (`WMS`)

Mobile-first, offline-first, route-oriented. Touch interactions and Bluetooth printing.

### US-WMS-001 — Log in to the WMS app
**As** Agent **I want** to log in on my phone **so that** I can start my route.
**P:** P1 · **Size:** M · **Surface:** WMS
**AC:**
- First login requires online connectivity; subsequent logins work offline within token TTL.

### US-WMS-002 — Sync today's route, prices, and balances
**As** Agent **I want** to sync the day's data before I leave the depot **so that** I have everything offline.
**P:** P1 · **Size:** L · **Surface:** WMS
**AC:**
- Sync downloads: assigned route, customer list (with current debt balances), price list, item catalogue, active promotions.
- Sync progress is visible; partial sync is recoverable.

### US-WMS-003 — Load the van (create a sales list)
**As** Agent or Storekeeper **I want** to record what I am loading onto the van **so that** stock leaves the branch correctly.
**P:** P1 · **Size:** M · **Surface:** WMS, Web · **Entities:** `sales_list`, `sales_list_line`
**AC:**
- Each item + quantity is captured; on post, `TRANSFER_OUT` from the branch to the agent's virtual "van" location.

### US-WMS-004 — View today's customer list
**As** Agent **I want** to see my customers for today **so that** I can plan the visit order.
**P:** P1 · **Size:** S · **Surface:** WMS
**AC:**
- Map view (Phase 2) and list view available.
- Each entry shows last visit, current debt, last order value.

### US-WMS-005 — Capture a sale at a customer site
**As** Agent **I want** to build a cart and capture a sale at the customer's site **so that** I can complete a visit.
**P:** P1 · **Size:** L · **Surface:** WMS · **Entities:** `sales_sheet_sale`, `sales_sheet_sale_line`
**AC:**
- I can search the catalogue, scan a barcode, add to cart, apply allowed discounts.
- Tender choice: cash or credit.
- Stock-on-van computed locally is decremented; the system warns if a line exceeds stock on the van.

### US-WMS-006 — Capture a payment against existing debt
**As** Agent **I want** to record a receipt against a customer's existing debt **so that** their balance updates.
**P:** P2 · **Size:** M · **Surface:** WMS · **Entities:** `sales_receipt`

### US-WMS-007 — Print or share a receipt
**As** Agent **I want** to print to a Bluetooth thermal printer or share by SMS/email **so that** the customer has proof.
**P:** P1 · **Size:** L · **Surface:** WMS
**AC:**
- The active printer is selected once and remembered.
- If the printer is offline, the sale proceeds and the receipt is queued.

### US-WMS-008 — Record a sales expense
**As** Agent **I want** to record fuel, tolls, or parking expenses **so that** they offset against the day's cash.
**P:** P1 · **Size:** S · **Surface:** WMS · **Entities:** `sales_expense`

### US-WMS-009 — Capture GPS at a visit (opt-in)
**As** Agent **I want** my visits to record approximate GPS **so that** route compliance can be reviewed.
**P:** P2 · **Size:** M · **Surface:** WMS
**AC:**
- GPS capture is non-blocking; offline operation is unaffected by GPS availability.

### US-WMS-010 — Submit end-of-day sales sheet
**As** Agent **I want** to submit my sales sheet at end of route **so that** the day's activity is finalised.
**P:** P1 · **Size:** L · **Surface:** WMS · **Entities:** `sales_sheet`
**AC:**
- The screen summarises: total sales, total cash, total credit, expenses, expected cash, declared cash, variance.
- Submission moves status to `SUBMITTED`; the sheet becomes read-only locally.

### US-WMS-011 — Approve or reject a sales sheet
**As** Manager or Accountant **I want** to review and approve a submitted sales sheet **so that** stock, debt, and cash post to the books.
**P:** P1 · **Size:** L · **Surface:** Web
**AC:**
- Rejection requires a reason and returns the sheet to the agent's app for amendment.
- Approval posts: `TRANSFER_IN` for unsold returns; `SALE`s; `debt_entry` for credit sales; `cash_entry` for collected cash net of expenses.
- A domain event `SalesSheetApproved.v1` is emitted.

### US-WMS-012 — Auto-update the WMS app
**As** Agent **I want** the app to update without a manual install **so that** I am always on a working version.
**P:** P1 · **Size:** L · **Surface:** WMS
**AC:**
- Updates are pushed via the configured channel (Play / sideload).
- A blocking dialog appears if the installed version is below `min_supported_api`.

---

# Epic 9: Debt Management (`DEBT`)

### US-DEBT-001 — View a customer's debt position
**As** Salesperson or Accountant **I want** to see a customer's open invoices and balance **so that** I can answer queries.
**P:** P1 · **Size:** S · **Surface:** Web · **Entities:** `debt_entry`
**AC:**
- The page shows: open invoices, allocated receipts, customer credit, ageing buckets.

### US-DEBT-002 — View a supplier's payable position
**As** Accountant **I want** to see what we owe each supplier **so that** I can plan payments.
**P:** P1 · **Size:** S · **Surface:** Web

### US-DEBT-003 — Run a debt ageing report
**As** Accountant or Manager **I want** to age receivables and payables by 0-30 / 31-60 / 61-90 / 90+ buckets **so that** I can chase overdue accounts.
**P:** P1 · **Size:** M · **Surface:** Web · **Entities:** `debt_entry`

### US-DEBT-004 — Write off a debt with dual approval
**As** Accountant **I want** to write off uncollectable debt **so that** ageing reflects reality.
**P:** P2 · **Size:** M · **Surface:** Web · **Entities:** `debt_writeoff`
**AC:**
- Requires Supervisor + Accountant authorisations (both recorded).
- Posts a compensating `debt_entry` and logs the reason.

### US-DEBT-005 — Generate a customer statement
**As** Accountant **I want** to generate a customer statement as PDF **so that** I can send it.
**P:** P1 · **Size:** M · **Surface:** Web
**AC:**
- Filters: customer, date range. Output: invoices, receipts, allocations, running balance.

### US-DEBT-006 — Generate a supplier statement
**As** Accountant **I want** to generate a supplier statement **so that** I can reconcile.
**P:** P1 · **Size:** M · **Surface:** Web

### US-DEBT-007 — Track debt assignments and follow-up notes
**As** Accountant or Manager **I want** to leave notes against a customer's debt **so that** follow-up actions are traceable.
**P:** P2 · **Size:** M · **Surface:** Web

---

# Epic 10: Production (`PROD`)

### US-PROD-001 — Define a BOM
**As** Operator or Manager **I want** to define a BOM for a product **so that** batch production can plan materials.
**P:** P2 · **Size:** L · **Surface:** Web · **Entities:** `bom`, `bom_line`
**AC:**
- I capture output item, output qty, output UoM, version, valid-from, expected yield %, and inputs with qty and wastage %.

### US-PROD-002 — Version a BOM
**As** Operator **I want** to create a new BOM version when a recipe changes **so that** historical batches keep their original BOM.
**P:** P2 · **Size:** S · **Surface:** Web
**AC:**
- The new version sets `valid_from`; the previous version's `valid_to` is set automatically.

### US-PROD-003 — Plan a batch
**As** Operator **I want** to plan a batch for a product and quantity **so that** material requirements are pre-computed.
**P:** P2 · **Size:** M · **Surface:** Web · **Entities:** `production_batch`, `production_consumption`
**AC:**
- The system computes planned consumption from the active BOM and item-branch balances.
- Insufficient stock is flagged but does not block planning.

### US-PROD-004 — Start a batch
**As** Operator **I want** to start a planned batch **so that** consumption can begin.
**P:** P2 · **Size:** S · **Surface:** Web
**AC:**
- Status moves to `IN_PROGRESS`; `started_at` and `started_by` are set.

### US-PROD-005 — Record actual material consumption and finished output
**As** Operator **I want** to record actual consumed quantities and actual output **so that** variance is captured.
**P:** P2 · **Size:** L · **Surface:** Web · **Entities:** `production_consumption`, `production_output`
**AC:**
- On completion: status moves to `COMPLETED`; `PROD_CONSUME` and `PROD_OUTPUT` stock moves post.
- Yield variance (actual / expected) is computed and visible.

### US-PROD-006 — Run a custom production (no BOM)
**As** Operator **I want** to record ad-hoc production not driven by a BOM **so that** one-off transformations are captured.
**P:** P2 · **Size:** M · **Surface:** Web

### US-PROD-007 — Record a conversion between items
**As** Storekeeper or Operator **I want** to convert one item into another **so that** repacks, conversions, and corrections are auditable.
**P:** P2 · **Size:** M · **Surface:** Web · **Entities:** `conversion`

### US-PROD-008 — Run a production variance report
**As** Manager **I want** to see planned vs actual material usage and yields **so that** I can spot inefficiency.
**P:** P2 · **Size:** M · **Surface:** Web

---

# Epic 11: Day Management (`DAY`)

### US-DAY-001 — Open the business day at a branch
**As** Manager **I want** to open the business day **so that** transactions can be posted.
**P:** P1 · **Size:** S · **Surface:** Web · **Entities:** `business_day`
**AC:**
- At most one `OPEN` business day exists per branch.
- Opening a day records `opened_by` and `opened_at`.

### US-DAY-002 — End-of-day per branch
**As** Manager **I want** to end the business day **so that** the day is closed and reporting is consistent.
**P:** P1 · **Size:** L · **Surface:** Web
**AC:**
- Pre-flight checks: all till sessions closed, all GRNs posted, no orphan production batches, all WMS sheets submitted (not necessarily approved).
- On success: status moves to `CLOSED`, Z-summary PDF generated, next day's record auto-created.
- Failing checks are listed and link to the offending document.

### US-DAY-003 — Override and post into a closed business day
**As** Manager with `BUSINESS_DAY.OVERRIDE` **I want** to back-date an entry **so that** corrections can be made.
**P:** P2 · **Size:** M · **Surface:** Web · **Entities:** `business_day_override`
**AC:**
- Requires a written reason.
- The override is recorded; the affected entity carries a marker so reports can highlight it.

### US-DAY-004 — View business-day status across branches
**As** HQ or Manager **I want** to see which branches have open days and which have closed **so that** I can chase laggards.
**P:** P2 · **Size:** S · **Surface:** Web

### US-DAY-005 — Auto-warn before a business day expires
**As** Manager **I want** a warning if a business day has been open too long **so that** I don't forget to close it.
**P:** P2 · **Size:** S · **Surface:** Web

---

# Epic 12: HR (light) (`HR`)

### US-HR-001 — Create an employee
**As** Admin or HR **I want** to create an employee **so that** they appear in scheduling and reporting.
**P:** P1 · **Size:** S · **Surface:** Web · **Entities:** `party`, `employee`

### US-HR-002 — Link an employee to an app user
**As** Admin **I want** to link an employee to an app user **so that** their actions in the system are attributable.
**P:** P1 · **Size:** S · **Surface:** Web

### US-HR-003 — Plan and record shifts
**As** Manager **I want** to plan shifts and record actuals **so that** I can match staffing to till sessions.
**P:** P2 · **Size:** M · **Surface:** Web · **Entities:** `shift`

### US-HR-004 — Enrol a biometric for an employee
See US-IAM-011.

### US-HR-005 — Terminate an employee
**As** Admin or HR **I want** to set a termination date **so that** an employee is removed from active rosters.
**P:** P2 · **Size:** S · **Surface:** Web
**AC:**
- Linked `app_user` (if any) is also deactivated; refresh tokens revoked.

---

# Epic 13: Reporting (`RPT`)

### US-RPT-001 — Run a daily sales report
**As** Manager **I want** a daily sales report for my branch **so that** I can see the day's performance.
**P:** P1 · **Size:** M · **Surface:** Web
**AC:**
- Filters: date, branch, agent. Columns: invoice no, customer, total, payment terms.

### US-RPT-002 — Run a daily summary report
**As** Manager **I want** sales + purchases + cash summary **so that** I have a one-pager on my desk.
**P:** P1 · **Size:** M · **Surface:** Web

### US-RPT-003 — Run a Z-history report
**As** Accountant **I want** all Z-reports for a date range **so that** I can audit till closes.
**P:** P1 · **Size:** S · **Surface:** Web

### US-RPT-004 — Run a stock card report
**As** Storekeeper **I want** a stock card for any item-branch and date range **so that** I can trace variances.
**P:** P1 · **Size:** M · **Surface:** Web

### US-RPT-005 — Run fast / slow moving items reports
**As** Merchandiser **I want** to see fast and slow moving items **so that** I can adjust ordering.
**P:** P2 · **Size:** M · **Surface:** Web

### US-RPT-006 — Run a negative-stock report
**As** Storekeeper **I want** to see all items in negative stock **so that** I can investigate.
**P:** P1 · **Size:** S · **Surface:** Web

### US-RPT-007 — Run a supplier or customer statement
See US-DEBT-005, US-DEBT-006.

### US-RPT-008 — Export a report to PDF / Excel / CSV
**As** any user **I want** to export the current report view **so that** I can share it.
**P:** P1 · **Size:** M · **Surface:** Web
**AC:**
- Exports run as background jobs for results > 5000 rows; small exports stream directly.

### US-RPT-009 — Schedule a report by email
**As** Manager **I want** to schedule a report to be emailed daily/weekly **so that** I don't have to log in to read it.
**P:** P2 · **Size:** L · **Surface:** Web

### US-RPT-010 — Run a group-consolidated report
**As** HQ **I want** to run reports across companies **so that** I can see group performance.
**P:** P3 · **Size:** L · **Surface:** Web

---

# Epic 14: Integrations (`INT`)

### US-INT-001 — Configure a fiscal printer
**As** Admin **I want** to select a fiscal-printer driver per branch **so that** POS receipts are compliant.
**P:** P2 · **Size:** L · **Surface:** Web, POS

### US-INT-002 — Subscribe to domain events via webhook
**As** Admin or external integrator **I want** to register a webhook URL for a set of event types **so that** my external system stays in sync.
**P:** P2 · **Size:** M · **Surface:** Web · **Entities:** `event_subscription`, `event_delivery`
**AC:**
- Webhook deliveries are HMAC-signed; the secret is shown only once at creation.
- Retries with exponential backoff; failed deliveries are visible.

### US-INT-003 — Export to an accounting package
**As** Accountant **I want** to export a date range of transactions to my accounting package's format **so that** I can post the GL.
**P:** P3 · **Size:** XL · **Surface:** Web
**AC:**
- Configurable chart-of-accounts mapping per company.
- Supported formats: CSV, QuickBooks IIF, or a generic JSON consumable by middleware (decided per deployment).

### US-INT-004 — Import a bank statement for reconciliation
**As** Accountant **I want** to import a CSV/OFX bank statement **so that** I can match payments to receipts.
**P:** P3 · **Size:** L · **Surface:** Web

### US-INT-005 — Integrate a mobile-money payment gateway
**As** Cashier or Agent **I want** to receive mobile-money payments directly from a customer prompt **so that** I don't need a separate device.
**P:** P3 · **Size:** XL · **Surface:** POS, WMS

---

# Epic 15: Platform (cross-cutting) (`PLAT`)

### US-PLAT-001 — Show system health and sync status
**As** Manager **I want** a system-health page **so that** I know which branches are syncing.
**P:** P1 · **Size:** M · **Surface:** Web
**AC:**
- Shows per-branch: open business day, open till sessions, last successful sync per till and per agent, pending domain events.

### US-PLAT-002 — Receive in-app notifications
**As** any user **I want** notifications for things requiring my attention **so that** I don't miss them.
**P:** P2 · **Size:** L · **Surface:** Web, POS, WMS
**AC:**
- Examples: a sales sheet awaiting your approval; an LPO pending approval; a Z-report variance over threshold.

### US-PLAT-003 — Toggle a feature flag
**As** Admin **I want** to toggle a feature flag at a chosen scope **so that** I can release progressively.
**P:** P1 · **Size:** M · **Surface:** Web · **Entities:** `feature_flag`, `feature_flag_override`
**AC:**
- Resolution preview shows what the flag would evaluate to for a sample user.
- All changes are audited.

### US-PLAT-004 — View product-analytics dashboards
**As** Admin or HQ **I want** to see feature-usage dashboards **so that** I can prioritise improvements.
**P:** P2 · **Size:** L · **Surface:** Web
**AC:**
- Embedded PostHog dashboards filtered to my org.

### US-PLAT-005 — Auto-update the POS app
**As** the POS device **I want** to install updates outside of an open till session **so that** cashiers are never interrupted mid-shift.
**P:** P1 · **Size:** L · **Surface:** POS

### US-PLAT-006 — Manage release channels per branch
**As** Admin **I want** to assign devices to stable / beta / canary channels **so that** rollouts are progressive.
**P:** P2 · **Size:** M · **Surface:** Web

### US-PLAT-007 — Export PII for a subject access request
**As** Admin **I want** to export everything we hold about a named party **so that** I can satisfy a compliance request.
**P:** P2 · **Size:** L · **Surface:** Web

### US-PLAT-008 — Delete / anonymise PII for a party
**As** Admin **I want** to anonymise a party while preserving transaction history **so that** I can satisfy an erasure request without breaking the books.
**P:** P2 · **Size:** L · **Surface:** Web
**AC:**
- Replaces `name`, `phone`, `email`, addresses with `[ERASED-{id}]`.
- Transactions referencing the party remain intact (immutable postings).

### US-PLAT-009 — View and configure number-sequence gaps
**As** Admin or Accountant **I want** a report of number-sequence gaps **so that** I can explain them to auditors.
**P:** P2 · **Size:** S · **Surface:** Web

### US-PLAT-010 — Use a single canonical web ERP build (not two)
**As** Admin **I want** one ERP build per release, not two diverging forks **so that** I don't have to choose between them.
**P:** P1 · **Size:** XL · **Surface:** Web
**AC:**
- The 70 files diverging between `orbix-erp` and `orbix-erp-x` are decided per feature: merged in, dropped, or behind a feature flag.

### US-PLAT-011 — Replay an event range to a subscriber
**As** Admin **I want** to re-dispatch a range of domain events to a webhook subscriber **so that** I can recover from missed deliveries.
**P:** P2 · **Size:** M · **Surface:** Web

### US-PLAT-012 — Monitor sync-queue depth per device
**As** Admin **I want** to see which tills or phones have a growing outbox **so that** I can intervene before issues become incidents.
**P:** P2 · **Size:** M · **Surface:** Web

---

# Epic 16: AI / LLM (Phase 3) (`AI`)

All AI features are advisory; humans confirm. See [ARCHITECTURE.md §12](ARCHITECTURE.md).

### US-AI-001 — Configure an AI provider
**As** Admin **I want** to choose an AI provider (hosted or self-hosted) for this deployment **so that** AI features respect data-sovereignty constraints.
**P:** P3 · **Size:** L · **Surface:** Web
**AC:**
- Default is "disabled". Switching providers writes an audit row.
- Each AI feature has its own enable flag.

### US-AI-002 — Ask a natural-language report question
**As** Manager **I want** to type "show sales of beverages last week vs same week last year" **so that** I get a quick answer.
**P:** P3 · **Size:** XL · **Surface:** Web
**AC:**
- NL is translated to a constrained query over existing report definitions; never free-form SQL.
- The generated report is auditable — both the question and the generated query are stored on the `report_run`.

### US-AI-003 — OCR a vendor invoice into a draft GRN
**As** Storekeeper **I want** to photograph a supplier invoice and get a draft GRN **so that** I don't re-key lines.
**P:** P3 · **Size:** XL · **Surface:** Web, WMS
**AC:**
- The draft GRN is editable; nothing posts until the storekeeper confirms.
- Items are matched against `item_supplier.supplier_item_code` first, then by name.

### US-AI-004 — Daily anomaly digest
**As** Manager **I want** a daily list of anomalies (debt ageing breaks, void rate spikes, stock variances) **so that** I focus my day.
**P:** P3 · **Size:** L · **Surface:** Web
**AC:**
- Detection runs on the domain event log; thresholds are configurable.
- Each item links to the relevant detail screen.

### US-AI-005 — Field-agent visit summary from dictation
**As** Agent **I want** to dictate a short note at end of route **so that** an assistant turns it into a structured visit summary on my sheet.
**P:** P3 · **Size:** L · **Surface:** WMS

---

# Backlog conventions

- **New stories** get the next free ID in their epic. IDs are never reused.
- **Renames** are allowed; the ID stays the same.
- **Splits**: if a story grows beyond Size L, split it into two with new IDs and a note linking them.
- **Closure**: a story is "done" when all AC pass, automated tests cover it, and product-analytics events are emitted where applicable.
- **Cross-references**: where a story depends on another, cite the ID inline rather than duplicating AC.

---

# Story counts by priority and surface

| | Web | POS | WMS | Backend | All |
|---|---|---|---|---|---|
| **P1** | 51 | 17 | 8 | 2 | — |
| **P2** | 20 | 1 | 3 | 1 | — |
| **P3** | 8 | 1 | 1 | 0 | — |

(Counts are indicative; cross-surface stories appear in their primary column.)

---

# Phase 1.1 — Supermarket scope additions

These stories accompany the Phase 1.1 scope additions (see [PRD.md §14](PRD.md), [docs/design/PHASE-1.1-ADDITIONS.md](docs/design/PHASE-1.1-ADDITIONS.md)).

## Epic 17: Admin (branch / section / currency / FX) (`ADMIN`)

### US-ADMIN-001 — Create a section within a branch
**As an** HQ admin, **I want to** add a section (Bakery, Butchery, Deli, etc.) to a branch, **so that** tills and BOMs can be assigned to it and section-level P&L is captured. **P1.**

### US-ADMIN-002 — Assign a section manager
**As an** HQ admin, **I want to** name a manager for each section, **so that** approval flows route to them. **P1.**

### US-ADMIN-003 — Enable a currency for the company
**As an** HQ admin, **I want to** enable additional ISO currencies (USD, EUR alongside UGX), **so that** tills can accept foreign tender. **P1.**

### US-ADMIN-004 — Quote an FX rate
**As a** treasurer, **I want to** post today's UGX→USD rate, **so that** POS converts tender amounts correctly. **P1.**

### US-ADMIN-005 — Deactivate a section
**As an** HQ admin, **I want to** deactivate a section once no open tills / BOMs reference it, **so that** the org tree stays clean. **P2.**

### US-ADMIN-006 — Configure accepted currencies per till
**As a** branch manager, **I want to** declare which foreign currencies a till accepts, **so that** cashiers can't tender an unsupported currency. **P2.**

### US-ADMIN-007 — Bulk-import sections from CSV
**As a** chain admin, **I want to** roll out sections to many new branches at once. **P3.**

## Epic 18: Orders — Layby & Pre-Order (`ORD`)

### US-ORD-001 — Create a layby
**As a** cashier or back-office user, **I want to** create a layby for a customer with a deposit and reservation period, **so that** the customer can pay in instalments. **P1.**

### US-ORD-002 — Pay an instalment toward a layby
**As a** cashier, **I want to** record additional payments against an open layby, **so that** the balance reduces. **P1.**

### US-ORD-003 — Collect a fully-paid layby
**As a** cashier, **I want to** scan a paid-up layby at the till and hand over the goods, **so that** ownership transfers and the reservation flips to a sale. **P1.**

### US-ORD-004 — Create a pre-order (production-tied)
**As a** cashier, **I want to** take a pre-order for a custom cake or platter, **so that** the bakery / juice section is scheduled to produce it. **P1.**

### US-ORD-005 — Cancel an order with deposit-refund policy applied
**As a** cashier with manager approval, **I want to** cancel an order and release the reservation, refunding per policy. **P1.**

### US-ORD-006 — Auto-expire abandoned orders
**As a** system job, **I want to** find orders past their `reserved_until` and mark them `EXPIRED`. **P1.**

### US-ORD-007 — List a customer's open orders
**As a** cashier, **I want to** see all open layby / pre-orders for a customer at scan time. **P2.**

### US-ORD-008 — Notify customer before expiry
**As an** orders job, **I want to** SMS the customer before an order expires. **P2.**

### US-ORD-009 — Print / share an order receipt
**As a** cashier, **I want to** give the customer a printable / emailed / SMS confirmation. **P2.**

## Epic 19: Gift Cards (`GC`)

### US-GC-001 — Issue a gift card at POS
**As a** cashier, **I want to** issue a new gift card for a stated value, **so that** the customer pays for it and walks away with a redeemable card. **P1.**

### US-GC-002 — Look up gift card balance
**As a** cashier, **I want to** scan a card and see the balance without redeeming. **P1.**

### US-GC-003 — Redeem a gift card as POS tender
**As a** cashier, **I want to** apply a gift card as a tender method during checkout. **P1.**

### US-GC-004 — Refund a gift-card-tendered sale
**As a** cashier, **I want to** credit the gift card back when a sale paid (partly) by gift card is refunded. **P2.**

### US-GC-005 — Freeze a lost / stolen gift card
**As a** branch manager, **I want to** freeze a card so it cannot be redeemed. **P1.**

### US-GC-006 — Unfreeze a gift card
**As a** branch manager, **I want to** unfreeze a previously frozen card. **P2.**

### US-GC-007 — Auto-expire gift cards
**As a** scheduled job, **I want to** mark cards past `expires_at` as `EXPIRED` and post the breakage ledger entry. **P2.**

### US-GC-008 — Gift card redemption rate report
**As a** finance user, **I want to** see issued vs redeemed values to forecast liability. **P2.**

## Stories appended to existing epics

### Epic 4 (STOCK) — batch / expiry / internal-consumption

- **US-STOCK-011** Capture batch number + expiry on a GRN line (P1)
- **US-STOCK-012** FEFO consumption at POS / back-office for batch-tracked items (P1)
- **US-STOCK-013** Expiring-soon report (P1)
- **US-STOCK-014** Mark an entire batch as recalled (manager + reason) (P2)
- **US-STOCK-015** Record internal consumption (canteen / display / samples / donation / maintenance) with authoriser + category + reason (P1)
- **US-STOCK-016** Section-tagged stock transfer (between sections of the same branch) (P2)

### Epic 3 (CAT) — weighed items + batches

- **US-CAT-015** Flag an item as `is_weighed` with a weighing unit (P1)
- **US-CAT-016** Parse embedded-weight EAN-13 at POS (P1)
- **US-CAT-017** Flag an item as `tracks_batches` (P1)
- **US-CAT-018** Bulk-edit weighed / batch-tracked flags (P2)

### Epic 7 (POS) — refund / FX tender / gift card tender

- **US-POS-019** Refund a same-day sale with receipt up to threshold (P1)
- **US-POS-020** Manager-PIN refund above threshold / without receipt (P1)
- **US-POS-021** Tender in a foreign currency at till (P1)
- **US-POS-022** Tender via gift card (P1)
- **US-POS-023** Apply staff price tier on employee badge scan (P2)
- **US-POS-024** Section-stamping on every POS sale (P1)

### Epic 10 (PROD) — production extensions

- **US-PROD-009** Record wastage with category + reason (P1)
- **US-PROD-010** Advance batch lifecycle through hot / cold / discounted / donated / write-off (P1)
- **US-PROD-011** Pack-by-weight at output (P2)
- **US-PROD-012** Sub-recipe references (parent BOM consuming child BOM) (P1)
- **US-PROD-013** Section-owned BOMs (P1)

### Epic 11 (DAY) — multi-currency close

- **US-DAY-006** Compute close-till variance per currency (P1)

### Epic 13 (RPT) — section + production reports

- **US-RPT-011** Section P&L per branch per period (P1)
- **US-RPT-012** Production yield + wastage by section (P1)
- **US-RPT-013** Gift card outstanding-liability report (P1)
- **US-RPT-014** Layby / pre-order ageing report (P2)

---

*End of User Stories. See [PRD.md](PRD.md), [ARCHITECTURE.md](ARCHITECTURE.md), [DATA-MODEL.md](DATA-MODEL.md).*
