-- Orbix Engine — multi-currency cash book (F6.2). Per Phase 1.1 §202:
--   cash_book PK extends from (branch, account, business_date) to
--   (branch, account, currency_code, business_date) so the projection
--   splits per tender currency (US-DAY-006 per-currency variance).
-- cash_entry gains FX columns so the ledger carries enough to drive
-- per-currency reporting without joining back to the source doc:
--   - currency_code already on the table — re-interpreted as TENDER currency
--     (was de-facto functional under F6.1; pre-existing rows are functional
--     so the re-interpretation backfills cleanly with fx_rate_snapshot = 1).
--   - tender_amount is the value in the row's currency_code.
--   - fx_rate_snapshot is the rate used to back-convert to functional amount.
--   - amount stays the functional-currency-converted value used for cross-
--     currency totals and the existing F6.1 idempotency / projection logic.
-- Pre-existing rows (dev DBs only — this is a clean-build project) backfill
-- to tender_amount = amount and fx_rate_snapshot = 1 via the column DEFAULTs.
--
-- The cash_book PK swap is dialect-specific (MySQL silently ignores the
-- pk_cash_book name so DROP CONSTRAINT pk_cash_book fails on MySQL; Postgres
-- preserves the named constraint). The dialect-aware swap lives in
-- mysql/V44_1__cash_book_pk_swap.sql + postgres/V44_1__cash_book_pk_swap.sql.

ALTER TABLE cash_entry ADD COLUMN fx_rate_snapshot DECIMAL(20, 8) NOT NULL DEFAULT 1;
ALTER TABLE cash_entry ADD COLUMN tender_amount    DECIMAL(18, 4);
UPDATE cash_entry SET tender_amount = amount WHERE tender_amount IS NULL;
