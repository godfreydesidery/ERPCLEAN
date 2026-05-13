-- MySQL emulates Hibernate sequences via a table. One row per logical sequence.
CREATE TABLE hibernate_sequence (
    sequence_name VARCHAR(80)  NOT NULL PRIMARY KEY,
    next_val      BIGINT       NOT NULL
);

INSERT INTO hibernate_sequence (sequence_name, next_val) VALUES ('domain_event_seq', 1);
INSERT INTO hibernate_sequence (sequence_name, next_val) VALUES ('item_seq', 1);
