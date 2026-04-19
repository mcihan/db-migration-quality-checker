-- Postgres seed for db-migration-quality-checker local tests.
-- Mirrors the DB2 source seed exactly so a DB2 <-> Postgres pair shows all
-- checks PASSED; pairing Postgres with MySQL will show the same mismatches
-- MySQL already has baked in.

-- USERS: PK on ID, secondary index on EMAIL.
CREATE TABLE USERS (
    ID          INTEGER       NOT NULL,
    NAME        VARCHAR(100)  NOT NULL,
    EMAIL       VARCHAR(150),
    CREATED_AT  TIMESTAMP     NOT NULL,
    PRIMARY KEY (ID)
);
CREATE INDEX IDX_USERS_EMAIL ON USERS (EMAIL);

INSERT INTO USERS VALUES
    (1,  'Alice',   'alice@example.com',   '2024-01-01 10:00:00'),
    (2,  'Bob',     'bob@example.com',     '2024-01-02 10:00:00'),
    (3,  'Carol',   'carol@example.com',   '2024-01-03 10:00:00'),
    (4,  'Dave',    'dave@example.com',    '2024-01-04 10:00:00'),
    (5,  'Eve',     'eve@example.com',     '2024-01-05 10:00:00'),
    (6,  'Frank',   'frank@example.com',   '2024-01-06 10:00:00'),
    (7,  'Grace',   'grace@example.com',   '2024-01-07 10:00:00'),
    (8,  'Heidi',   'heidi@example.com',   '2024-01-08 10:00:00'),
    (9,  'Ivan',    'ivan@example.com',    '2024-01-09 10:00:00'),
    (10, 'Judy',    'judy@example.com',    '2024-01-10 10:00:00');

-- ORDERS: PK on ID, plus an index on USER_ID. Full set of 10 rows.
CREATE TABLE ORDERS (
    ID          INTEGER        NOT NULL,
    USER_ID     INTEGER        NOT NULL,
    AMOUNT      DECIMAL(10,2)  NOT NULL,
    STATUS      VARCHAR(20)    NOT NULL,
    CREATED_AT  TIMESTAMP      NOT NULL,
    PRIMARY KEY (ID)
);
CREATE INDEX IDX_ORDERS_USER_ID ON ORDERS (USER_ID);

INSERT INTO ORDERS VALUES
    (1001, 1,  49.99,  'PAID',      '2024-02-01 09:00:00'),
    (1002, 2,  19.50,  'PAID',      '2024-02-02 09:00:00'),
    (1003, 3,  120.00, 'PENDING',   '2024-02-03 09:00:00'),
    (1004, 1,  5.25,   'PAID',      '2024-02-04 09:00:00'),
    (1005, 4,  75.00,  'CANCELLED', '2024-02-05 09:00:00'),
    (1006, 5,  210.10, 'PAID',      '2024-02-06 09:00:00'),
    (1007, 6,  14.00,  'PAID',      '2024-02-07 09:00:00'),
    (1008, 7,  99.99,  'PENDING',   '2024-02-08 09:00:00'),
    (1009, 2,  7.80,   'PAID',      '2024-02-09 09:00:00'),
    (1010, 8,  300.00, 'PAID',      '2024-02-10 09:00:00');

-- LOOKUP_TABLE: no primary key — checker falls back to full-row matching.
CREATE TABLE LOOKUP_TABLE (
    CODE        VARCHAR(20)   NOT NULL,
    LABEL       VARCHAR(100)  NOT NULL,
    SORT_ORDER  INTEGER       NOT NULL
);

INSERT INTO LOOKUP_TABLE VALUES
    ('PAID',      'Paid',      1),
    ('PENDING',   'Pending',   2),
    ('CANCELLED', 'Cancelled', 3),
    ('REFUNDED',  'Refunded',  4);
