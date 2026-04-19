-- MySQL seed for db-migration-quality-checker local tests.
-- Matches the DB2 schema in seed/db2/01_schema.sql, with a few deliberate
-- mismatches on ORDERS and LOOKUP_TABLE so the report shows both PASSED and
-- FAILED blocks.

CREATE DATABASE IF NOT EXISTS TARGET_DB;
USE TARGET_DB;

GRANT ALL PRIVILEGES ON TARGET_DB.* TO 'mysqluser'@'%';
FLUSH PRIVILEGES;

-- USERS: matches DB2 exactly — all checks should PASS.
CREATE TABLE USERS (
    ID          INT          NOT NULL,
    NAME        VARCHAR(100) NOT NULL,
    EMAIL       VARCHAR(150),
    CREATED_AT  TIMESTAMP    NOT NULL,
    PRIMARY KEY (ID),
    INDEX IDX_USERS_EMAIL (EMAIL)
);

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

-- ORDERS: intentionally missing the last row (ID 1010) AND order 1003 has a
-- different STATUS. Row-count and random-data checks should FAIL for this one.
CREATE TABLE ORDERS (
    ID          INT            NOT NULL,
    USER_ID     INT            NOT NULL,
    AMOUNT      DECIMAL(10,2)  NOT NULL,
    STATUS      VARCHAR(20)    NOT NULL,
    CREATED_AT  TIMESTAMP      NOT NULL,
    PRIMARY KEY (ID),
    INDEX IDX_ORDERS_USER_ID (USER_ID)
);

INSERT INTO ORDERS VALUES
    (1001, 1,  49.99,  'PAID',     '2024-02-01 09:00:00'),
    (1002, 2,  19.50,  'PAID',     '2024-02-02 09:00:00'),
    (1003, 3,  120.00, 'PAID',     '2024-02-03 09:00:00'),   -- DB2 had 'PENDING' — value mismatch
    (1004, 1,  5.25,   'PAID',     '2024-02-04 09:00:00'),
    (1005, 4,  75.00,  'CANCELLED','2024-02-05 09:00:00'),
    (1006, 5,  210.10, 'PAID',     '2024-02-06 09:00:00'),
    (1007, 6,  14.00,  'PAID',     '2024-02-07 09:00:00'),
    (1008, 7,  99.99,  'PENDING',  '2024-02-08 09:00:00'),
    (1009, 2,  7.80,   'PAID',     '2024-02-09 09:00:00');
    -- (1010 intentionally missing → row-count diff of 1)

-- LOOKUP_TABLE: matches DB2 exactly — row count + random data should PASS.
CREATE TABLE LOOKUP_TABLE (
    CODE        VARCHAR(20)  NOT NULL,
    LABEL       VARCHAR(100) NOT NULL,
    SORT_ORDER  INT          NOT NULL
);

INSERT INTO LOOKUP_TABLE VALUES
    ('PAID',      'Paid',      1),
    ('PENDING',   'Pending',   2),
    ('CANCELLED', 'Cancelled', 3),
    ('REFUNDED',  'Refunded',  4);
