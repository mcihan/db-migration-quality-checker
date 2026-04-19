# DB Migration Quality Checker

[![CI](https://github.com/mcihan/db-migration-quality-checker/actions/workflows/ci.yml/badge.svg)](https://github.com/mcihan/db-migration-quality-checker/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://adoptium.net/)

**Compares two databases and tells you what doesn't match.** Read-only — it never writes.

**Five checks per table**, each producing its own text report:

- Column names
- Column metadata (type, nullability, default, auto-increment)
- Indexes (columns + uniqueness)
- Row count
- Random-row sample (values per column)

```
┌─────────────┐      ┌─────────────────────────┐      ┌─────────────┐
│  Source DB  │◄─────┤  db-migration-quality-  ├─────►│  Target DB  │
└─────────────┘      │        checker          │      └─────────────┘
                     └────────────┬────────────┘
                                  │
                                  ▼
                          ./report/*.txt
```

**Supported databases** (any pairing, either side):

- DB2
- MySQL
- PostgreSQL

Example of one failed row-count block:

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

## Quick start

The fastest way to try it: run the bundled script. It boots local databases, seeds sample data, and runs the checker.

```bash
./scripts/run-local-check.sh                                # DB2 → MySQL (default)
./scripts/run-local-check.sh --source mysql --target postgres
./scripts/run-local-check.sh --source postgres --target db2
```

Reports appear under `./report/`. First DB2 boot takes a few minutes; MySQL and Postgres are seconds. Pairs that don't include DB2 skip its boot entirely.

Other script flags: `--skip-up` (containers already running), `--down` (stop), `--down-volumes` (stop + wipe).

---

## Use it against your own databases

Two things to set: **where your databases are** (env vars) and **which tables to compare** (`data/tables.csv`).

### 1. Environment variables

| Variable | Example | Notes |
|---|---|---|
| `SOURCE_TYPE` | `DB2` | One of `DB2`, `MYSQL`, `POSTGRES`. |
| `SOURCE_JDBC_URL` | `jdbc:db2://host:50000/MYDB` | Standard JDBC URL for your engine. |
| `SOURCE_USERNAME` | `alice` | |
| `SOURCE_PASSWORD` | `…` | |
| `TARGET_TYPE` | `POSTGRES` | Same set of values as `SOURCE_TYPE`. |
| `TARGET_JDBC_URL` | `jdbc:postgresql://host:5432/mydb` | |
| `TARGET_USERNAME` | `alice` | |
| `TARGET_PASSWORD` | `…` | |

The right JDBC driver is picked automatically based on `*_TYPE`.

### 2. `data/tables.csv`

```csv
SourceSchema,TargetSchema,TableName,PrimaryKeyName,QueryCondition,IsHexId
SOURCE_SCHEMA,TARGET_DB,USERS,ID,,false
SOURCE_SCHEMA,TARGET_DB,ORDERS,ID,WHERE CREATED_AT > '2024-01-01',false
SOURCE_SCHEMA,TARGET_DB,LOOKUP_TABLE,,,
```

| Column | Required | What it means |
|---|---|---|
| `SourceSchema` | yes | Schema / database name on the source (DB2 schema, MySQL database, Postgres schema — typically `public`). |
| `TargetSchema` | yes | Same, for the target. |
| `TableName` | yes | Table name; assumed identical on both sides. |
| `PrimaryKeyName` | no | If set, random-data check does PK lookups; otherwise it matches full rows. |
| `QueryCondition` | no | `WHERE …` clause appended to the row-count query (e.g. `WHERE STATUS='ACTIVE'`). |
| `IsHexId` | no | `true` when the PK is stored as binary/UUID on the target (needs `hex`/`encode` for lookups). |

### 3. Run it

```bash
./mvnw clean package -DskipTests
java -jar target/db-migration-quality-checker-1.0.0-SNAPSHOT.jar
```

Reports land in `./report/<CHECK_NAME>_TEST_RESULT.txt`.

---

## What it reports

Per table, five checks — all run in parallel. Each produces one text file.

| Check | File | What it verifies |
|---|---|---|
| Column names | `COLUMN_NAME_COMPARISON_TEST_RESULT.txt` | Both sides have the same set of column names. |
| Column metadata | `COLUMN_METADATA_COMPARISON_TEST_RESULT.txt` | Type, nullability, default, auto-increment match. Known cross-engine equivalences are applied (e.g. `INT*` ↔ `INT*`, `TIMESTAMP*` ↔ `TIMESTAMP*`, `CHARACTER` ↔ `CHAR`/`BINARY`). |
| Indexes | `INDEX_COMPARISON_TEST_RESULT.txt` | Every source index has a matching target index (same columns, same uniqueness). Extra indexes on the target are flagged as warnings. |
| Row count | `TABLE_ROW_COUNT_COMPARISON_TEST_RESULT.txt` | `COUNT(*)` matches, optionally filtered by `QueryCondition`. |
| Random data | `RANDOM_DATA_COMPARISON_TEST_RESULT.txt` | First N source rows are looked up on the target and their column values compared. |

Every report starts with a summary (start/end time, passed/failed counts) and lists `[Test PASSED]` / `[Test FAILED]` blocks per table, each with the exact SQL used — so any failure can be reproduced with a paste into your DB client.

Run just one check by setting one of `ONLY_RUN_ROW_COUNT`, `ONLY_COLUMN_METADATA`, `ONLY_RANDOM_DATA` to `true`. Adjust the random-data sample size with `RANDOM_DATA_COUNT` (default 10,000).

---

## Run in Docker

```bash
docker build -f docker/Dockerfile -t db-migration-quality-checker .

docker run --rm \
  -e SOURCE_TYPE=DB2    -e SOURCE_JDBC_URL=… -e SOURCE_USERNAME=… -e SOURCE_PASSWORD=… \
  -e TARGET_TYPE=MYSQL  -e TARGET_JDBC_URL=… -e TARGET_USERNAME=… -e TARGET_PASSWORD=… \
  -v "$PWD/data:/app/data" \
  -v "$PWD/report:/app/report" \
  db-migration-quality-checker
```

On Linux, add `--add-host=host.docker.internal:host-gateway` if you're using `host.docker.internal` in a JDBC URL.

---

## Tests

```bash
./mvnw test                         # 59 unit tests, no Docker needed (< 10 s)
./mvnw verify                       # + MySQL + Postgres + cross-engine ITs (~1 min)
./mvnw verify -Dgroups=integration  # + heavy DB2 ↔ MySQL end-to-end IT
```

One-time setup for fast re-runs:

```bash
echo "testcontainers.reuse.enable=true" >> ~/.testcontainers.properties
```

---

## Requirements

- **Java 25** (Temurin recommended)
- **Maven 3.9+** — the wrapper `./mvnw` is included, nothing to install globally
- **Docker** — only for the sandbox script and the containerised run
- Network access to both databases

---

## FAQ / troubleshooting

**Where do I put `tables.csv`?**
At the project root, in `data/tables.csv`. When running with Docker, mount it to `/app/data/tables.csv`.

**Can I use the same engine on both sides?**
Yes for Postgres↔Postgres etc. — you just need two reachable instances with different JDBC URLs.

**Random-data check is slow.**
It samples `RANDOM_DATA_COUNT` rows per table. Lower it for smoke runs: `RANDOM_DATA_COUNT=100`.

**Column-type mismatches I expected to be fine show as failures.**
The metadata check applies cross-engine equivalences (see the table above). If your types aren't covered, add a rule in `check/support/ColumnTypeCompatibility.java` — it's a handful of lines.

**Postgres auto-increment shows as `false`.**
It's detected via the `nextval(…)` default convention (`SERIAL` / `IDENTITY`). Columns with a custom sequence default won't be auto-detected.

**Maven can't resolve a driver.**
All three drivers are on Maven Central. Check your `~/.m2/settings.xml` mirror configuration.

**Linux container can't reach localhost DBs.**
`host.docker.internal` isn't resolved by default; pass `--add-host=host.docker.internal:host-gateway` or use the real host IP.

---

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the development workflow, architecture walkthrough, and how to add a new check, engine, or report format. Community rules: [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md). Security reports: [`SECURITY.md`](SECURITY.md).

## License

Apache License 2.0 — see [`LICENSE`](LICENSE).
