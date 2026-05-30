-- DIALECT-SPECIFIC: Postgres sequence for fiscal_receipt primary key.
-- Mirrors the pattern of every other module that uses Hibernate SEQUENCE strategy.
CREATE SEQUENCE fiscal_receipt_seq START WITH 1 INCREMENT BY 50;
