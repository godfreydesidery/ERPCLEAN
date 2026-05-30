-- Orbix Engine — offline-sync spine (US-POS-017 / US-POS-018).
-- Design: docs/design/slice-sync-spine.md.
-- DB-agnostic: runs unchanged on MariaDB 11 + PostgreSQL 15 (both support
-- CREATE SEQUENCE and BIGINT sequences).

-- -----------------------------------------------------------------------
-- 1.  Shared monotonic change_seq sequence
--     Used to stamp reference-table rows on every insert/update so the
--     pull endpoint can page deltas with  WHERE change_seq > :cursor.
--     One shared sequence keeps the cursor simple (one scalar covers all
--     datasets in v1).  See design §3.3 for the decision rationale.
-- -----------------------------------------------------------------------
CREATE SEQUENCE sync_change_seq START WITH 1 INCREMENT BY 1;

-- -----------------------------------------------------------------------
-- 2.  change_seq column on reference tables the till pulls
--     Each table: one nullable BIGINT to start (NULL = row pre-dates sync;
--     treated as change_seq = 0 by the pull query).  The service stamps
--     the column from the sequence on every write going forward.
--
--     Tables targeted for Phase 1 POS pull:
--       item, vat_group, price_list_item, price_list   (catalog/price)
--       customer                                        (party)
--
--     item_barcode pulls via item.change_seq (same upsert row for v1).
--     stock balance rows (item_branch_balance) get their own entry too.
-- -----------------------------------------------------------------------
ALTER TABLE item              ADD COLUMN change_seq BIGINT;
ALTER TABLE vat_group         ADD COLUMN change_seq BIGINT;
ALTER TABLE price_list_item   ADD COLUMN change_seq BIGINT;
ALTER TABLE price_list        ADD COLUMN change_seq BIGINT;
-- party: change_seq on the Party row (status, code, name) drives the customer dataset.
-- The customer role table (party_id = FK) has no independent change_seq; service stamps
-- party.change_seq on every customer create/update/archive.
ALTER TABLE party             ADD COLUMN change_seq BIGINT;

-- -----------------------------------------------------------------------
-- 3.  client_op_id + unique-constraint on device-reachable transactional
--     tables (cash_pickup, petty_cash, till_session).
--     pos_sale already has these (uk_pos_sale_client_op, V34).
--     The idempotency constraint is (company_id, client_op_id) so two
--     companies' devices never collide and the index stays selective.
-- -----------------------------------------------------------------------
ALTER TABLE cash_pickup  ADD COLUMN client_op_id VARCHAR(40);
ALTER TABLE petty_cash   ADD COLUMN client_op_id VARCHAR(40);
ALTER TABLE till_session ADD COLUMN client_op_id VARCHAR(40);

-- Unique constraints: (company_id, client_op_id).
-- Partial-unique indexes on nullable columns differ slightly across
-- dialects, so we use a plain UNIQUE(company_id, client_op_id) and rely
-- on the server pre-checking for NULL before inserting (non-sync paths
-- leave client_op_id NULL; the constraint allows multiple NULLs in SQL
-- standard semantics, which both MariaDB and Postgres honour).
ALTER TABLE cash_pickup  ADD CONSTRAINT uk_cash_pickup_client_op  UNIQUE (company_id, client_op_id);
ALTER TABLE petty_cash   ADD CONSTRAINT uk_petty_cash_client_op   UNIQUE (company_id, client_op_id);
ALTER TABLE till_session ADD CONSTRAINT uk_till_session_client_op UNIQUE (company_id, client_op_id);

-- -----------------------------------------------------------------------
-- 3b. Backfill change_seq for any rows that pre-date this migration.
--     On a fresh (recreated) DB these UPDATEs affect zero rows — they are
--     defensive for the ephemeral-migration policy and any future stable
--     schema promotion.
--
--     Strategy: set change_seq = id (the PK is already monotonic and positive)
--     so pre-existing rows get a determinate, stable, ordered value and are
--     visible to a pull with cursor=0.  New writes use NEXT VALUE FOR
--     sync_change_seq which starts at 1, so after backfill we must restart
--     the sequence above the largest backfilled value.  We use a conservative
--     restart at 1,000,000 — well above any realistic seed-data id — so
--     live writes always get values strictly greater than all backfilled ids.
--     A future bulk-import that exceeds 1,000,000 rows would need this raised,
--     but that is not a concern for Phase 1 POS reference data.
-- -----------------------------------------------------------------------
UPDATE item            SET change_seq = id            WHERE change_seq IS NULL;
UPDATE vat_group       SET change_seq = id            WHERE change_seq IS NULL;
UPDATE price_list_item SET change_seq = id            WHERE change_seq IS NULL;
UPDATE price_list      SET change_seq = id            WHERE change_seq IS NULL;
UPDATE party           SET change_seq = id            WHERE change_seq IS NULL;

-- Restart sync_change_seq above all backfilled values.
-- Both MariaDB 11 and PostgreSQL 15 support ALTER SEQUENCE ... RESTART WITH.
ALTER SEQUENCE sync_change_seq RESTART WITH 1000000;

-- -----------------------------------------------------------------------
-- 4.  Tombstone retention config is application-level (orbix.sync.*);
--     no schema artefact needed.  The pull query surfaces archived/inactive
--     rows as deletes by checking status = 'ARCHIVED'/'INACTIVE'.
-- -----------------------------------------------------------------------

-- -----------------------------------------------------------------------
-- 5.  Sequence for Hibernate-mapped entities that were missing sequences.
--     (till_session was using allocationSize=50 against till_session_seq
--     which is created by a later migration if absent — creating it here
--     is idempotent on fresh DBs.)
-- -----------------------------------------------------------------------
-- No new sequences needed: all POS entities already have their sequences
-- from V32 / V34 / V42.
