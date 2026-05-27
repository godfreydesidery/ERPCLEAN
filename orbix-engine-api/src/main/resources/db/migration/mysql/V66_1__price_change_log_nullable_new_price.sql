-- DIALECT-SPECIFIC: column-nullability change uses vendor DDL syntax
-- (MySQL/MariaDB `MODIFY`; Postgres `ALTER COLUMN ... DROP NOT NULL`).
-- new_price becomes nullable so a discontinuation (old price -> none) is
-- recorded in the price-change history just like any other change.

ALTER TABLE price_change_log
    MODIFY new_price DECIMAL(18, 4) NULL;
