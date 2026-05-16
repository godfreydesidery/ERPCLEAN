-- F6.2 cash_book PK swap (MySQL variant). MySQL names every PRIMARY KEY
-- "PRIMARY" regardless of any CONSTRAINT name supplied at CREATE TABLE,
-- so `DROP CONSTRAINT pk_cash_book` fails. Use `DROP PRIMARY KEY` instead.
ALTER TABLE cash_book DROP PRIMARY KEY,
    ADD PRIMARY KEY (branch_id, account, currency_code, business_date);
