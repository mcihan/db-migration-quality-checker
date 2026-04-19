# DB Migration Quality Checker

[![CI](https://github.com/dbmigrationqualitychecker/db-migration-quality-checker/actions/workflows/ci.yml/badge.svg)](https://github.com/dbmigrationqualitychecker/db-migration-quality-checker/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6db33f.svg)](https://spring.io/projects/spring-boot)

A Spring Boot tool that verifies the quality of a **DB2 → MySQL** data migration by running a suite of parallel comparison checks across two live databases and producing a human‑readable report per check type.

It does **not** modify either database — it only reads from the source (DB2) and target (MySQL) and reports any discrepancies it finds.

---

## What it checks

For every table listed in `data/tables.csv`, the tool runs the following checks (in parallel, by default):

| # | Check | Report file | What it verifies |
|---|-------|-------------|------------------|
| 1 | **Column Name Comparison** | `COLUMN_NAME_COMPARISON_TEST_RESULT.txt` | Both sides expose the same set of column names (alphabetical compare). |
| 2 | **Column Metadata Comparison** | `COLUMN_METADATA_COMPARISON_TEST_RESULT.txt` | For each column: data type, nullability, default value, and auto‑increment flag match (with known DB2↔MySQL type equivalences such as `CHARACTER`↔`CHAR`/`BINARY`, `TIMESTAMP`↔`TIMESTAMP`, `INT*`↔`INT*`). |
| 3 | **Index Comparison** | `INDEX_COMPARISON_TEST_RESULT.txt` | Every DB2 index has a MySQL counterpart with the same column set and uniqueness. Warns when MySQL has extra indexes that DB2 does not. |
| 4 | **Table Row Count Comparison** | `TABLE_ROW_COUNT_COMPARISON_TEST_RESULT.txt` | `COUNT(*)` on both sides matches (optionally filtered by a `WHERE` clause per table). |
| 5 | **Random Data Comparison** | `RANDOM_DATA_COMPARISON_TEST_RESULT.txt` | Samples the first N rows from DB2 and verifies each row exists on MySQL with matching column values. When a primary key is provided, it looks up rows by ID (with optional HEX handling for UUID columns stored as `VARBINARY`); otherwise it matches on the full row. |

Each report starts with a summary header (start/end time, duration, total/success/failure counts) and then lists per‑table `[Test PASSED]` / `[Test FAILED]` blocks — including the exact SQL queries used so failures can be reproduced by hand.

---

## How it works

```
┌────────────┐       ┌──────────────────────────┐       ┌────────────┐
│   DB2      │◄──────┤  db-migration-quality-   ├──────►│   MySQL    │
│ (source)   │       │        checker           │       │ (target)   │
└────────────┘       └────────────┬─────────────┘       └────────────┘
                                  │
                                  ▼
                          ./report/*.txt
```

- Entry point: [`DbMigrationQualityCheckerApplication`](src/main/java/com/dbmigrationqualitychecker/DbMigrationQualityCheckerApplication.java) — a `CommandLineRunner` that fans out the five checks onto `CompletableFuture`s and waits for them all.
- Tables to check are loaded by [`TableProvider`](src/main/java/com/dbmigrationqualitychecker/service/TableProvider.java) from `data/tables.csv` (relative to the working directory).
- Each DB is accessed via a separate `NamedParameterJdbcTemplate` configured in [`DBConfiguration`](src/main/java/com/dbmigrationqualitychecker/config/DBConfiguration.java).
- Reports are written to `./report/<TYPE>_TEST_RESULT.txt` by [`ReportService`](src/main/java/com/dbmigrationqualitychecker/report/ReportService.java). The directory is created on demand.

---

## Requirements

- **Java 25**
- **Maven 3.9+** (a Maven wrapper `./mvnw` is included)
- Network reachability to both a DB2 instance and a MySQL instance
- Optional: **Docker** / **Docker Compose** if you prefer the containerised run
- The IBM **DB2 JDBC driver** (`com.ibm.db2:jcc`) — pulled from Maven Central. Pin a different version if needed:

  ```bash
  ./mvnw package -Ddb2-driver-version=11.5.8.0
  ```

---

## Configuring the tables to check

Create/edit `data/tables.csv` at the project root. Format:

```csv
SourceSchema,TargetSchema,TableName,PrimaryKeyName,QueryCondition,IsHexId
SOURCE_SCHEMA,TARGET_DB,USERS,ID,,false
SOURCE_SCHEMA,TARGET_DB,ORDERS,ID,WHERE CREATED > '2024-01-01',false
SOURCE_SCHEMA,TARGET_DB,LOOKUP_TABLE,,,
SOURCE_SCHEMA,TARGET_DB,USER_CARD,ID,,true
```

Columns:

| Column | Required | Description |
|---|---|---|
| `SourceSchema` | yes | DB2 schema name. |
| `TargetSchema` | yes | MySQL schema/database name. |
| `TableName` | yes | Name of the table; assumed identical on both sides. |
| `PrimaryKeyName` | no | Primary key column. If provided, Random Data Comparison looks rows up by ID on MySQL instead of matching on every column. |
| `QueryCondition` | no | Optional `WHERE …` clause appended to the row‑count query (e.g. `WHERE status='ACTIVE'`). |
| `IsHexId` | no | `true` when the PK is stored as `VARBINARY`/UUID on MySQL and needs `lower(hex(id))` for lookups. |

---

## Configuration (environment variables)

All settings are read from `src/main/resources/application.yml` and can be overridden via environment variables.

### Database connections

Source and target are DB-agnostic: set `SOURCE_TYPE` / `TARGET_TYPE` to either `DB2` or `MYSQL` and the app picks the right JDBC driver + dialect automatically.

| Variable | Default | Description |
|---|---|---|
| `SOURCE_TYPE` | `DB2` | Source database engine. One of `DB2`, `MYSQL`. |
| `SOURCE_JDBC_URL` | `jdbc:db2://localhost:50000/SOURCEDB` | Source JDBC URL. |
| `SOURCE_USERNAME` | `db2user` | Source user. |
| `SOURCE_PASSWORD` | `changeme` | Source password. |
| `TARGET_TYPE` | `MYSQL` | Target database engine. One of `DB2`, `MYSQL`. |
| `TARGET_JDBC_URL` | `jdbc:mysql://localhost:3306/TARGETDB` | Target JDBC URL. |
| `TARGET_USERNAME` | `mysqluser` | Target user. |
| `TARGET_PASSWORD` | `changeme` | Target password. |

### Test selection / behaviour

By default **all five checks run**. Set exactly one of the following to `true` to run only that check:

| Variable | Default | Description |
|---|---|---|
| `ONLY_RUN_ROW_COUNT` | `false` | Run only the row‑count check. |
| `ONLY_COLUMN_METADATA` | `false` | Run only the column metadata check. |
| `ONLY_RANDOM_DATA` | `false` | Run only the random data check. |
| `RANDOM_DATA_COUNT` | `10000` | Number of rows sampled per table for the random data check. |
| `IS_INJECTED_TABLE_FILE_ACTIVE` | `false` | Flag used when mounting a custom `tables.csv` via volume. |

If multiple `ONLY_*` flags are `true`, the precedence is: row count → random data → column metadata (see the `if/else if` chain in `DbMigrationQualityCheckerApplication#compare`).

---

## Running the tool

### Option A — Docker Compose (recommended)

The bundled `docker/docker-compose.yml` builds the image locally and mounts the two directories the app needs:

- `./data` → `/app/data` — so the container reads your `tables.csv`.
- `./report` → `/app/report` — so reports end up on your host.

Edit the environment block in `docker/docker-compose.yml` to point at your DB2 / MySQL hosts and credentials, then:

```bash
# 1. Make sure data/tables.csv exists and lists the tables you want checked.
mkdir -p data report
# edit data/tables.csv ...

# 2. Build and start. It runs once and exits.
cd docker && docker compose up --build && cd ..

# 3. Inspect results.
ls report/
#   COLUMN_NAME_COMPARISON_TEST_RESULT.txt
#   COLUMN_METADATA_COMPARISON_TEST_RESULT.txt
#   INDEX_COMPARISON_TEST_RESULT.txt
#   TABLE_ROW_COUNT_COMPARISON_TEST_RESULT.txt
#   RANDOM_DATA_COMPARISON_TEST_RESULT.txt
```

> **Note:** `host.docker.internal` is used in the defaults so the container can reach DBs running on the host machine (macOS/Windows). On Linux, either add `extra_hosts: ["host.docker.internal:host-gateway"]` to the compose service or replace with the actual host/IP.

### Option B — Local run with Maven

```bash
# 1. Build the fat jar (skipping tests).
./mvnw clean package -DskipTests

# 2. Make sure data/tables.csv exists at the project root.

# 3. Run, overriding any config via env vars.
SOURCE_TYPE=DB2 \
SOURCE_JDBC_URL="jdbc:db2://localhost:50000/SOURCEDB" \
SOURCE_USERNAME=db2user SOURCE_PASSWORD=changeme \
TARGET_TYPE=MYSQL \
TARGET_JDBC_URL="jdbc:mysql://localhost:3306/TARGETDB" \
TARGET_USERNAME=mysqluser TARGET_PASSWORD=changeme \
RANDOM_DATA_COUNT=5000 \
java -jar target/db-migration-quality-checker-1.0.0-SNAPSHOT.jar
```

Reports will appear under `./report/`.

To run only a single check:

```bash
ONLY_RUN_ROW_COUNT=true java -jar target/db-migration-quality-checker-1.0.0-SNAPSHOT.jar
```

### Option C — Build your own Docker image

```bash
docker build -f docker/Dockerfile -t db-migration-quality-checker:local .
docker run --rm \
  -e SOURCE_TYPE=DB2 \
  -e SOURCE_JDBC_URL="jdbc:db2://host.docker.internal:50000/SOURCEDB" \
  -e SOURCE_USERNAME=db2user -e SOURCE_PASSWORD=changeme \
  -e TARGET_TYPE=MYSQL \
  -e TARGET_JDBC_URL="jdbc:mysql://host.docker.internal:3306/TARGETDB" \
  -e TARGET_USERNAME=mysqluser -e TARGET_PASSWORD=changeme \
  -v "$PWD/data:/app/data" \
  -v "$PWD/report:/app/report" \
  db-migration-quality-checker:local
```

### Option D — Tests only (no DBs required)

```bash
./mvnw test            # 61 unit tests, < 10s
./mvnw verify          # adds a fast MySQL-backed integration test (Testcontainers)
./mvnw verify -Dgroups=integration  # also includes the heavier DB2 + MySQL end-to-end suite
```

---

## Reading a report

Each report file starts with a summary header and is followed by `[Test PASSED]` or `[Test FAILED]` blocks separated by a line of dashes. Failed blocks include:

- A human‑readable description of the mismatch (e.g. `Total data count does not match. DB2: 1234, MYSQL: 1230, Diff: 4`).
- The exact SQL query (or queries) used, so you can paste them straight into a DB client to investigate.
- For the random‑data check, a `FAILURE DETAILS` section listing each failing column, DB2 value, and MySQL value.

Example (row‑count failure):

```
[Test FAILED] : ORDERS
Total data count does not match.
DB2:   1200345
MYSQL: 1200340

Diff:  5
DB2 has more data.

QUERIES:
select count(1) from SOURCE_SCHEMA.ORDERS
```

---

## Project layout

```
.
├── docker/
│   ├── Dockerfile                 # Multi-stage: Maven build + Temurin 21 JRE
│   └── docker-compose.yml         # Local build, env-driven DB connections
├── pom.xml                        # Spring Boot 3.4, Java 25
├── data/
│   └── tables.csv                 # Runtime input: which tables to check
├── src/main/java/com/dbmigrationqualitychecker/
│   ├── DbMigrationQualityCheckerApplication.java   # CLI runner, orchestrates checks
│   ├── config/                    # DataSource + @Value wiring
│   ├── repository/                # DB2 and MySQL JDBC access
│   ├── service/                   # The 5 comparison services + TableProvider
│   ├── report/                    # Report writer + Table/ReportType models
│   └── util/                      # Row mappers, duration formatting
├── src/main/resources/
│   └── application.yml            # Default config + env var bindings
├── src/test/                      # Unit + testcontainers-based integration tests
└── .github/                       # CI workflows, issue + PR templates, dependabot
```

---

## Tests

| Command | What runs | Typical duration |
|---|---|---|
| `./mvnw test` | All unit tests (61). | < 10s |
| `./mvnw verify` | Unit + fast MySQL integration test. | ~1 min |
| `./mvnw verify -Dgroups=integration` | Also runs the heavy DB2 + MySQL end-to-end suite. | 5–15 min first run, seconds on reuse |

Testcontainers is configured for container reuse. One-time setup:

```bash
echo "testcontainers.reuse.enable=true" >> ~/.testcontainers.properties
```

---

## Troubleshooting

- **`Couldn't read table csv file!`** — the tool could not find `data/tables.csv` in the working directory. When running with Docker, make sure you mounted `./data` to `/app/data` (the compose file already does this).
- **Maven can't resolve `com.ibm.db2:jcc`** — check your `~/.m2/settings.xml` mirror configuration; the driver is on Maven Central.
- **Random Data check is slow** — it reads `RANDOM_DATA_COUNT` rows per table from DB2. Lower it for quick smoke runs.
- **Container can't reach local DBs on Linux** — `host.docker.internal` is not resolved by default; add `extra_hosts: ["host.docker.internal:host-gateway"]` to the compose service, or point the JDBC URLs at the real host/IP.
- **`Column missing in MySQL` false positives** — the metadata check compares on column‑name equality; confirm both sides use matching casing (the MySQL query already upper‑cases names).

---

## Contributing

Contributions welcome! Please read [`CONTRIBUTING.md`](CONTRIBUTING.md) for the development workflow, and [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) for community guidelines. Report security issues via [`SECURITY.md`](SECURITY.md).

## License

Licensed under the Apache License, Version 2.0 — see [`LICENSE`](LICENSE).
