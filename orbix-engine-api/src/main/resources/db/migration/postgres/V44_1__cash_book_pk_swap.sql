-- F6.2 cash_book PK swap (Postgres variant). Postgres preserves the
-- pk_cash_book name from V40's CONSTRAINT clause.
ALTER TABLE cash_book DROP CONSTRAINT pk_cash_book;
ALTER TABLE cash_book ADD CONSTRAINT pk_cash_book
    PRIMARY KEY (branch_id, account, currency_code, business_date);
