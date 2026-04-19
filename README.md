# DB Migration Quality Checker

[![CI](https://github.com/mcihan/db-migration-quality-checker/actions/workflows/ci.yml/badge.svg)](https://github.com/mcihan/db-migration-quality-checker/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6db33f.svg)](https://spring.io/projects/spring-boot)

A Spring Boot tool that verifies the quality of a database migration between any two of **DB2**, **MySQL**, and **PostgreSQL**. Point it at a source and a target, it runs five parallel checks, and writes a human-readable report per check.

It **never writes to either database** — it only reads from both and reports any discrepancies.

```
┌─────────────────┐      ┌─────────────────────────┐      ┌─────────────────┐
│  Source DB      │◄─────┤  db-migration-quality-  ├─────►│  Target DB      │
│  DB2 │ MySQL │  │      │        checker          │      │  DB2 │ MySQL │  │
│  Postgres       │      └────────────┬────────────┘      │  Postgres       │
└─────────────────┘                   │                   └─────────────────┘
                                      ▼
                              ./report/*.txt
```

Any pair is valid: DB2→MySQL, DB2→Postgres, MySQL→Postgres, Postgres→DB2, … The engine of each side is picked at runtime from `SOURCE_TYPE` / `TARGET_TYPE`.

---

## Quick start

```bash
# 1. Boot local DB2 + MySQL + Postgres containers, seed sample data, run any pair:
./scripts/run-local-check.sh                              # db2 → mysql
./scripts/run-local-check.sh --source mysql --target postgres
./scripts/run-local-check.sh --source postgres --target db2

# 2. Read the reports:
ls report/
```

Reports land in `report/<CHECK>_TEST_RESULT.txt`. Failures include the exact SQL that produced them so you can reproduce any mismatch by hand.

---

## What it checks

For every table in `data/tables.csv`, five checks run in parallel:

| # | Check | Report file | What it verifies |
|---|-------|-------------|------------------|
| 1 | **Column names** | `COLUMN_NAME_COMPARISON_TEST_RESULT.txt` | Both sides expose the same set of column names (alphabetical compare, case-normalised). |
| 2 | **Column metadata** | `COLUMN_METADATA_COMPARISON_TEST_RESULT.txt` | For each column: data type, nullability, default value, auto-increment flag. Known equivalences are applied (e.g. `INT*` ↔ `INT*`, `TIMESTAMP*` ↔ `TIMESTAMP*`, `CHARACTER` ↔ `CHAR`/`BINARY`). |
| 3 | **Indexes** | `INDEX_COMPARISON_TEST_RESULT.txt` | Every source index has a target counterpart with the same column set and uniqueness. Warns if the target has extra indexes. |
| 4 | **Row count** | `TABLE_ROW_COUNT_COMPARISON_TEST_RESULT.txt` | `COUNT(*)` matches on both sides (optionally filtered with a `WHERE` clause per table). |
| 5 | **Random data** | `RANDOM_DATA_COMPARISON_TEST_RESULT.txt` | Samples the first N rows from the source and checks each row exists on the target with matching column values. Uses primary-key lookup if available (with optional hex decoding for UUID columns stored as binary); otherwise matches by full row. |

Each report starts with a summary header (start/end time, duration, passed/failed counts) and then lists `[Test PASSED]` or `[Test FAILED]` blocks per table — each block includes the exact SQL queries used.

---

## Supported engines

| Engine     | Driver dep (Maven Central)  | JDBC URL example                                     |
|------------|-----------------------------|------------------------------------------------------|
| **DB2**    | `com.ibm.db2:jcc`           | `jdbc:db2://host:50000/MYDB`                         |
| **MySQL**  | `mysql:mysql-connector-java`| `jdbc:mysql://host:3306/mydb`                        |
| **Postgres** | `org.postgresql:postgresql` | `jdbc:postgresql://host:5432/mydb`                   |

Switch engine on either side by changing one env var. The tool loads the right driver and dialect automatically.

**Adding a new engine** is one new `DatabaseDialect` implementation + one enum case in `DataSourceConfig.dialectFor`. See [Extending](#extending) below.

---

## Requirements

- **Java 25** (Temurin recommended)
- **Maven 3.9+** (wrapper `./mvnw` included — no global Maven needed)
- Network reachability to both databases
- **Docker** / **Docker Compose** — optional, only for the containerised run and the local sandbox script

---

## Configuring the tables to check

Create `data/tables.csv`:

```csv
SourceSchema,TargetSchema,TableName,PrimaryKeyName,QueryCondition,IsHexId
SOURCE_SCHEMA,TARGET_DB,USERS,ID,,false
SOURCE_SCHEMA,TARGET_DB,ORDERS,ID,WHERE CREATED_AT > '2024-01-01',false
SOURCE_SCHEMA,TARGET_DB,LOOKUP_TABLE,,,
SOURCE_SCHEMA,TARGET_DB,USER_CARD,ID,,true
```

| Column | Required | Description |
|---|---|---|
| `SourceSchema` | yes | Schema name on the source. For DB2 this is a schema; for MySQL it's the database; for Postgres it's typically `public`. |
| `TargetSchema` | yes | Schema/database name on the target. |
| `TableName` | yes | Name of the table; assumed identical on both sides. |
| `PrimaryKeyName` | no | PK column. If set, the random-data check does PK lookups on the target; otherwise it matches on the full row. |
| `QueryCondition` | no | Optional `WHERE …` clause appended to the row-count query (e.g. `WHERE STATUS='ACTIVE'`). |
| `IsHexId` | no | `true` when the PK is stored as binary/UUID on the target and needs `lower(hex(id))` / `lower(encode(id,'hex'))` for lookups. |

> The `scripts/run-local-check.sh` helper generates this file automatically for whichever pair you pick.

---

## Configuration (environment variables)

All settings are read from `src/main/resources/application.yml` and can be overridden with environment variables.

### Database connections

Source and target are engine-agnostic: set `SOURCE_TYPE` / `TARGET_TYPE` to one of `DB2`, `MYSQL`, `POSTGRES`.

| Variable | Default | Description |
|---|---|---|
| `SOURCE_TYPE` | `DB2` | Source engine. One of `DB2`, `MYSQL`, `POSTGRES`. |
| `SOURCE_JDBC_URL` | `jdbc:db2://localhost:50000/SOURCEDB` | Source JDBC URL. |
| `SOURCE_USERNAME` | `db2user` | Source user. |
| `SOURCE_PASSWORD` | `changeme` | Source password. |
| `TARGET_TYPE` | `MYSQL` | Target engine. One of `DB2`, `MYSQL`, `POSTGRES`. |
| `TARGET_JDBC_URL` | `jdbc:mysql://localhost:3306/TARGETDB` | Target JDBC URL. |
| `TARGET_USERNAME` | `mysqluser` | Target user. |
| `TARGET_PASSWORD` | `changeme` | Target password. |

### Behaviour

By default **all five checks run**. Optionally run only one:

| Variable | Default | Description |
|---|---|---|
| `ONLY_RUN_ROW_COUNT` | `false` | Run only the row-count check. |
| `ONLY_COLUMN_METADATA` | `false` | Run only the column-metadata check. |
| `ONLY_RANDOM_DATA` | `false` | Run only the random-data check. |
| `RANDOM_DATA_COUNT` | `10000` | Rows sampled per table for the random-data check. |
| `IS_INJECTED_TABLE_FILE_ACTIVE` | `false` | Flag for mounting a custom `tables.csv` via volume. |

If more than one `ONLY_*` is `true`, the first match in this order wins: row-count → random-data → column-metadata.

---

## Running the tool

### Option A — Local sandbox script (easiest)

The helper script brings up the databases you need, seeds sample data, generates `data/tables.csv`, and runs the checker.

```bash
./scripts/run-local-check.sh                              # db2 → mysql (default)
./scripts/run-local-check.sh --source mysql --target postgres
./scripts/run-local-check.sh --source postgres --target db2
./scripts/run-local-check.sh --skip-up                    # containers already running
./scripts/run-local-check.sh --down                       # stop (keep data)
./scripts/run-local-check.sh --down-volumes               # stop + wipe data
```

Only the engines the chosen pair uses are started — so `mysql ↔ postgres` never boots DB2 (which takes several minutes on first boot).

### Option B — Local jar against your own DBs

```bash
./mvnw clean package -DskipTests

SOURCE_TYPE=POSTGRES \
SOURCE_JDBC_URL="jdbc:postgresql://source-host:5432/mydb" \
SOURCE_USERNAME=… SOURCE_PASSWORD=… \
TARGET_TYPE=MYSQL \
TARGET_JDBC_URL="jdbc:mysql://target-host:3306/mydb" \
TARGET_USERNAME=… TARGET_PASSWORD=… \
RANDOM_DATA_COUNT=5000 \
java -jar target/db-migration-quality-checker-1.0.0-SNAPSHOT.jar
```

### Option C — Docker (for CI or ephemeral environments)

```bash
docker build -f docker/Dockerfile -t db-migration-quality-checker:local .

docker run --rm \
  -e SOURCE_TYPE=DB2 \
  -e SOURCE_JDBC_URL="jdbc:db2://host.docker.internal:50000/MYDB" \
  -e SOURCE_USERNAME=… -e SOURCE_PASSWORD=… \
  -e TARGET_TYPE=POSTGRES \
  -e TARGET_JDBC_URL="jdbc:postgresql://host.docker.internal:5432/mydb" \
  -e TARGET_USERNAME=… -e TARGET_PASSWORD=… \
  -v "$PWD/data:/app/data" \
  -v "$PWD/report:/app/report" \
  db-migration-quality-checker:local
```

> On Linux, `host.docker.internal` isn't resolved by default — add `--add-host=host.docker.internal:host-gateway` or use the real host IP.

### Option D — Tests only (no DBs required)

```bash
./mvnw test                          # 59 unit tests — runs in seconds
./mvnw verify                        # adds MySQL + Postgres ITs and a cross-engine IT (~1 min)
./mvnw verify -Dgroups=integration   # also runs the heavy DB2+MySQL end-to-end IT (slow)
```

---

## Reading a report

Each report starts with a summary header, then blocks separated by a line of dashes. Every block contains:

- A `[Test PASSED]` or `[Test FAILED]` marker + table name.
- A human-readable description of the result.
- The exact SQL queries used.
- For random-data failures: a `FAILURE DETAILS` section listing each mismatching column, source value, and target value.

Example (row-count failure):

```
[Test FAILED] : ORDERS
Total data count does not match.
source: 1200345
target: 1200340

Diff:   5
source has more data.

QUERIES:
select count(1) from SOURCE_SCHEMA.ORDERS
```

---

## How it works

```
DbMigrationQualityCheckerApplication
            │
            ▼
       CheckRunner  ◄── one MigrationCheck per report type
            │            (ColumnNamesCheck, IndexCheck, …)
            ▼
  for every Table in tables.csv:
      check.execute(table) ──► CheckOutcome (Passed | Failed)
            │
            ▼
       ReportWriter  ◄── default TextFileReportWriter writes .txt files
```

- **`MigrationCheck`** is a `(Table) → CheckOutcome` strategy. Each of the five checks is one tiny class. Add a new check by writing one more.
- **`DatabaseDialect`** hides all SQL-dialect differences (system catalogs, pagination, type normalisation, binary-UUID handling). One impl per engine.
- **`DatabaseRepository`** is a thin JDBC facade over a dialect + connection, parameterised by `Side.SOURCE` / `Side.TARGET` so each side uses its own schema field of `Table`.
- **`ReportWriter`** is an interface — swap the default text file output for JSON/HTML later without touching any check.

---

## Project layout

```
.
├── docker/
│   ├── Dockerfile                 # Multi-stage: Maven build + Temurin 25 JRE
│   ├── docker-compose.yml         # Runtime stack for production-like use
│   ├── docker-compose.local.yml   # Local sandbox: DB2 + MySQL + Postgres
│   └── seed/                      # DDL + sample data per engine
│       ├── db2/01_schema.sql
│       ├── mysql/01_schema.sql
│       └── postgres/01_schema.sql
├── scripts/
│   └── run-local-check.sh         # Pair-aware local sandbox runner
├── data/
│   └── tables.csv                 # Runtime input: which tables to check
├── pom.xml                        # Spring Boot 3.5, Java 25
├── src/main/java/com/dbmigrationqualitychecker/
│   ├── DbMigrationQualityCheckerApplication.java   # Spring Boot entry point
│   ├── check/                     # MigrationCheck, CheckRunner, 5 *Check impls + support/
│   ├── dialect/                   # DatabaseDialect + Db2Dialect / MySqlDialect / PostgresDialect
│   ├── repository/                # DatabaseRepository (the generic JDBC facade)
│   ├── model/                     # Records: Table, ColumnDetails, IndexDetails, QueryResult, RecordData
│   ├── report/                    # ReportType + ReportWriter + TextFileReportWriter
│   └── config/                    # DataSourceConfig (wires two sides from env vars)
├── src/main/resources/
│   └── application.yml            # Env-var bindings + defaults
├── src/test/                      # Unit + Testcontainers integration tests
└── .github/                       # CI workflows, issue + PR templates, dependabot
```

---

## Extending

### Add a new check

1. Create `check/NewCheck.java` implementing `MigrationCheck`:
   ```java
   @Component
   class NewCheck implements MigrationCheck {
       public ReportType reportType() { return ReportType.NEW_COMPARISON; }
       public CheckOutcome execute(Table table) { … }
   }
   ```
2. Add `NEW_COMPARISON` to `ReportType`.
3. Done — Spring discovers it and the runner schedules it automatically.

### Add a new database engine

1. Add `ORACLE` (say) to `DatabaseType`.
2. Create `dialect/OracleDialect implements DatabaseDialect`.
3. Add one line in `DataSourceConfig.dialectFor(type)`: `case ORACLE -> new OracleDialect();`
4. Add the JDBC driver dep to `pom.xml`.

No other code changes needed.

### Add a new report format

1. Create `report/JsonReportWriter implements ReportWriter`, `@Component` it.
2. Remove `@Component` from `TextFileReportWriter` (or use `@Primary` / profiles).

---

## Tests

| Command | What runs | Typical duration |
|---|---|---|
| `./mvnw test` | 59 unit tests — no Docker needed. | < 10 s |
| `./mvnw verify` | Unit + fast ITs: MySQL, Postgres, MySQL↔Postgres cross-engine. | ~1 min |
| `./mvnw verify -Dgroups=integration` | Also the heavy DB2 + MySQL end-to-end IT. | 5–15 min first run, seconds thereafter via container reuse |

Testcontainers reuse (one-time):

```bash
echo "testcontainers.reuse.enable=true" >> ~/.testcontainers.properties
```

---

## Troubleshooting

- **`Couldn't read table csv file!`** — `data/tables.csv` not found in the working directory. With Docker, mount `./data` → `/app/data` (the compose file already does this).
- **Maven can't resolve a driver** — all three drivers are on Maven Central; check your `~/.m2/settings.xml` mirror configuration.
- **Random-data check is slow** — it reads `RANDOM_DATA_COUNT` rows per table from the source. Lower it for smoke runs.
- **Container can't reach local DBs on Linux** — `host.docker.internal` isn't resolved by default; add `extra_hosts: ["host.docker.internal:host-gateway"]` to the compose service or use the real host IP.
- **Column missing on one side, false positive** — column-name comparisons normalise to uppercase to paper over engine casing differences. If you still see false positives, double-check your `SourceSchema` / `TargetSchema` values in `tables.csv`.
- **Postgres auto-increment not detected** — the metadata check recognises `SERIAL` / `IDENTITY` via the `nextval(…)` default convention; custom sequences won't be auto-detected.

---

## Contributing

Contributions welcome! See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the development workflow and [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) for community guidelines. Report security issues privately — see [`SECURITY.md`](SECURITY.md).

## License

Licensed under the Apache License, Version 2.0 — see [`LICENSE`](LICENSE).
