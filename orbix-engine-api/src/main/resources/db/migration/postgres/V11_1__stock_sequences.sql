-- Native sequence for the stock ledger. item_branch_balance has a composite
-- natural PK, so it needs no sequence.
CREATE SEQUENCE stock_move_seq START WITH 1 INCREMENT BY 50;
