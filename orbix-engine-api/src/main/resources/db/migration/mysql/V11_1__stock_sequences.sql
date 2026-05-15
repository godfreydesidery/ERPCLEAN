-- Emulated sequence for the stock ledger. item_branch_balance has a composite
-- natural PK, so it needs no sequence.
INSERT INTO hibernate_sequence (sequence_name, next_val) VALUES ('stock_move_seq', 1);
