-- Native MariaDB sequences (target = MariaDB 10.3+; vanilla MySQL 8 does not
-- support CREATE SEQUENCE — switch to Postgres or use the postgres profile
-- if you're on stock MySQL).
CREATE SEQUENCE domain_event_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE item_seq         START WITH 1 INCREMENT BY 50;
