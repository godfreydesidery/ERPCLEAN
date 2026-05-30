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
