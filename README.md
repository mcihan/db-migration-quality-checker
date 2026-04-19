# DB Migration Quality Checker

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

- **Java 21**
- **Maven 3.9+** (a Maven wrapper `./mvnw` is included)
- Network reachability to both a DB2 instance and a MySQL instance
- Optional: **Docker** / **Docker Compose** if you prefer the containerised run
- The IBM **DB2 JDBC driver** — see the note below. The jar is bundled at the repo root for convenience.

### About the DB2 JDBC driver

IBM's DB2 JDBC driver is not available on Maven Central under the coordinates this project uses. Because of IBM's redistribution terms, you must install the bundled driver into your local Maven repository once before building:

```bash
mvn install:install-file \
  -Dfile=db2jcc4-4.19.72.jar \
  -DgroupId=com.ibm.db2 \
  -DartifactId=db2jcc4 \
  -Dversion=4.19.72 \
  -Dpackaging=jar
```

If your source DB2 is a newer server and you need a newer driver, install it under a version you choose and update the version in [`pom.xml`](pom.xml) accordingly. The `4.19.72` pin in this repo was chosen for compatibility with an older DB2 server.

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

| Variable | Default | Description |
|---|---|---|
| `DB2_JDBC_URL` | `jdbc:db2://localhost:50000/SOURCEDB` | DB2 JDBC URL. |
| `DB2_USERNAME` | `db2user` | DB2 user. |
| `DB2_PASSWORD` | `changeme` | DB2 password. |
| `MYSQL_JDBC_URL` | `jdbc:mysql://localhost:3306/TARGETDB` | MySQL JDBC URL. |
| `MYSQL_USERNAME` | `mysqluser` | MySQL user. |
| `MYSQL_PASSWORD` | `changeme` | MySQL password. |
| `DB2_DEFAULT_SCHEMA` | `SOURCE_SCHEMA` | Default DB2 schema (informational). |

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

The bundled `docker-compose.yml` builds the image locally and mounts the two directories the app needs:

- `./data` → `/app/data` — so the container reads your `tables.csv`.
- `./report` → `/app/report` — so reports end up on your host.

Edit the environment block in `docker-compose.yml` to point at your DB2 / MySQL hosts and credentials, then:

```bash
# 1. Make sure data/tables.csv exists and lists the tables you want checked.
mkdir -p data report
# edit data/tables.csv ...

# 2. Install the DB2 driver to your local Maven repo (one-time, see above).

# 3. Build and start. It runs once and exits.
docker compose up --build

# 4. Inspect results.
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
# 1. One-time: install the DB2 JDBC driver to your local Maven repo (see above).

# 2. Build the fat jar (skipping tests).
./mvnw clean package -DskipTests

# 3. Make sure data/tables.csv exists at the project root.

# 4. Run, overriding any config via env vars.
DB2_JDBC_URL="jdbc:db2://localhost:50000/SOURCEDB" \
DB2_USERNAME=db2user DB2_PASSWORD=changeme \
MYSQL_JDBC_URL="jdbc:mysql://localhost:3306/TARGETDB" \
MYSQL_USERNAME=mysqluser MYSQL_PASSWORD=changeme \
RANDOM_DATA_COUNT=5000 \
java -jar target/db-migration-quality-checker-0.0.1-SNAPSHOT.jar
```

Reports will appear under `./report/`.

To run only a single check:

```bash
ONLY_RUN_ROW_COUNT=true java -jar target/db-migration-quality-checker-0.0.1-SNAPSHOT.jar
```

### Option C — Build your own Docker image

```bash
docker build -t db-migration-quality-checker:local .
docker run --rm \
  -e DB2_JDBC_URL="jdbc:db2://host.docker.internal:50000/SOURCEDB" \
  -e DB2_USERNAME=db2user -e DB2_PASSWORD=changeme \
  -e MYSQL_JDBC_URL="jdbc:mysql://host.docker.internal:3306/TARGETDB" \
  -e MYSQL_USERNAME=mysqluser -e MYSQL_PASSWORD=changeme \
  -v "$PWD/data:/app/data" \
  -v "$PWD/report:/app/report" \
  db-migration-quality-checker:local
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
├── Dockerfile                     # Multi-stage: Maven build + Temurin 21 JRE
├── docker-compose.yml             # Local build, env-driven DB connections
├── pom.xml                        # Spring Boot 3.4, Java 21
├── data/
│   └── tables.csv                 # Runtime input: which tables to check
├── db2jcc4-4.19.72.jar            # IBM DB2 JDBC driver (install-file first)
├── src/main/java/com/dbmigrationqualitychecker/
│   ├── DbMigrationQualityCheckerApplication.java   # CLI runner, orchestrates checks
│   ├── config/                    # DataSource + @Value wiring
│   ├── repository/                # DB2 and MySQL JDBC access
│   ├── service/                   # The 5 comparison services + TableProvider
│   ├── report/                    # Report writer + Table/ReportType models
│   └── util/                      # Row mappers, duration formatting
├── src/main/resources/
│   └── application.yml            # Default config + env var bindings
└── src/test/                      # Unit + testcontainers-based integration tests
```

---

## Tests

Unit tests run as part of `./mvnw test`. Integration tests (grouped under the `integration` JUnit tag) use Testcontainers to spin up real DB2 and MySQL containers and run via Maven Failsafe:

```bash
./mvnw verify
```

---

## Troubleshooting

- **`Couldn't read table csv file!`** — the tool could not find `data/tables.csv` in the working directory. When running with Docker, make sure you mounted `./data` to `/app/data` (the compose file already does this).
- **Maven can't resolve `com.ibm.db2:db2jcc4:4.19.72`** — you haven't installed the bundled driver jar yet. See the *About the DB2 JDBC driver* section above.
- **DB2 driver errors after upgrading the driver** — older DB2 servers require matching‑era clients. If you must upgrade, install the new jar under a new version and update `pom.xml`.
- **Random Data check is slow** — it reads `RANDOM_DATA_COUNT` rows per table from DB2. Lower it for quick smoke runs.
- **Container can't reach local DBs on Linux** — `host.docker.internal` is not resolved by default; add `extra_hosts: ["host.docker.internal:host-gateway"]` to the compose service, or point the JDBC URLs at the real host/IP.
- **`Column missing in MySQL` false positives** — the metadata check compares on column‑name equality; confirm both sides use matching casing (the MySQL query already upper‑cases names).

---

## License

MIT — see [`LICENSE`](LICENSE) (add one before publishing).
