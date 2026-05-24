-- Sequences for the F1.4 catalog aggregates (tables created in V2).
-- uom_seq starts at 100: ids 1-8 are taken by the default unit seed in V2.
CREATE SEQUENCE uom_seq          START WITH 100 INCREMENT BY 50;
CREATE SEQUENCE vat_group_seq    START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE item_barcode_seq START WITH 1 INCREMENT BY 50;
