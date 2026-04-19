-- DB2 seed for db-migration-quality-checker local tests.
-- Creates SOURCE_SCHEMA and the three tables referenced by data/tables.csv.

CREATE SCHEMA SOURCE_SCHEMA;

-- USERS: PK on ID, secondary index on EMAIL.
CREATE TABLE SOURCE_SCHEMA.USERS (
    ID          INTEGER      NOT NULL,
    NAME        VARCHAR(100) NOT NULL,
    EMAIL       VARCHAR(150),
    CREATED_AT  TIMESTAMP    NOT NULL,
    PRIMARY KEY (ID)
);
CREATE INDEX SOURCE_SCHEMA.IDX_USERS_EMAIL ON SOURCE_SCHEMA.USERS (EMAIL);

INSERT INTO SOURCE_SCHEMA.USERS VALUES
    (1,  'Alice',   'alice@example.com',   TIMESTAMP '2024-01-01 10:00:00'),
    (2,  'Bob',     'bob@example.com',     TIMESTAMP '2024-01-02 10:00:00'),
    (3,  'Carol',   'carol@example.com',   TIMESTAMP '2024-01-03 10:00:00'),
    (4,  'Dave',    'dave@example.com',    TIMESTAMP '2024-01-04 10:00:00'),
    (5,  'Eve',     'eve@example.com',     TIMESTAMP '2024-01-05 10:00:00'),
    (6,  'Frank',   'frank@example.com',   TIMESTAMP '2024-01-06 10:00:00'),
    (7,  'Grace',   'grace@example.com',   TIMESTAMP '2024-01-07 10:00:00'),
    (8,  'Heidi',   'heidi@example.com',   TIMESTAMP '2024-01-08 10:00:00'),
    (9,  'Ivan',    'ivan@example.com',    TIMESTAMP '2024-01-09 10:00:00'),
    (10, 'Judy',    'judy@example.com',    TIMESTAMP '2024-01-10 10:00:00');

-- ORDERS: PK on ID, plus a FK-style index on USER_ID.
CREATE TABLE SOURCE_SCHEMA.ORDERS (
    ID          INTEGER        NOT NULL,
    USER_ID     INTEGER        NOT NULL,
    AMOUNT      DECIMAL(10,2)  NOT NULL,
    STATUS      VARCHAR(20)    NOT NULL,
    CREATED_AT  TIMESTAMP      NOT NULL,
    PRIMARY KEY (ID)
);
CREATE INDEX SOURCE_SCHEMA.IDX_ORDERS_USER_ID ON SOURCE_SCHEMA.ORDERS (USER_ID);

INSERT INTO SOURCE_SCHEMA.ORDERS VALUES
    (1001, 1,  49.99,  'PAID',     TIMESTAMP '2024-02-01 09:00:00'),
    (1002, 2,  19.50,  'PAID',     TIMESTAMP '2024-02-02 09:00:00'),
    (1003, 3,  120.00, 'PENDING',  TIMESTAMP '2024-02-03 09:00:00'),
    (1004, 1,  5.25,   'PAID',     TIMESTAMP '2024-02-04 09:00:00'),
    (1005, 4,  75.00,  'CANCELLED',TIMESTAMP '2024-02-05 09:00:00'),
    (1006, 5,  210.10, 'PAID',     TIMESTAMP '2024-02-06 09:00:00'),
    (1007, 6,  14.00,  'PAID',     TIMESTAMP '2024-02-07 09:00:00'),
    (1008, 7,  99.99,  'PENDING',  TIMESTAMP '2024-02-08 09:00:00'),
    (1009, 2,  7.80,   'PAID',     TIMESTAMP '2024-02-09 09:00:00'),
    (1010, 8,  300.00, 'PAID',     TIMESTAMP '2024-02-10 09:00:00');

-- LOOKUP_TABLE: no primary key — the checker falls back to full-row matching.
CREATE TABLE SOURCE_SCHEMA.LOOKUP_TABLE (
    CODE        VARCHAR(20)  NOT NULL,
    LABEL       VARCHAR(100) NOT NULL,
    SORT_ORDER  INTEGER      NOT NULL
);

INSERT INTO SOURCE_SCHEMA.LOOKUP_TABLE VALUES
    ('PAID',      'Paid',      1),
    ('PENDING',   'Pending',   2),
    ('CANCELLED', 'Cancelled', 3),
    ('REFUNDED',  'Refunded',  4);
