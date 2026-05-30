-- DIALECT-SPECIFIC: MariaDB/MySQL does not support CREATE SEQUENCE in the same DDL
-- as table creation in all configurations; fiscal_receipt uses a dedicated sequence
-- for its Hibernate SEQUENCE id generator (mirroring every other module's pattern).
CREATE SEQUENCE fiscal_receipt_seq START WITH 1 INCREMENT BY 50;
