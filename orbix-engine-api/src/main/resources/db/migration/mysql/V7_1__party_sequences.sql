-- Sequences for the F1.7 party aggregates. The role tables (customer /
-- supplier / employee / sales_agent) share the party primary key, so
-- they need no sequence of their own.
CREATE SEQUENCE party_seq         START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE party_address_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE party_contact_seq START WITH 1 INCREMENT BY 50;
