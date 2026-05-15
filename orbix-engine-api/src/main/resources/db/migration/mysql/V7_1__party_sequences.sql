-- Emulated sequences for the F1.7 party aggregates. The role tables
-- (customer / supplier / employee / sales_agent) share the party primary
-- key, so they need no sequence of their own.
INSERT INTO hibernate_sequence (sequence_name, next_val) VALUES ('party_seq', 1);
INSERT INTO hibernate_sequence (sequence_name, next_val) VALUES ('party_address_seq', 1);
INSERT INTO hibernate_sequence (sequence_name, next_val) VALUES ('party_contact_seq', 1);
