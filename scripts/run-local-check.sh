#!/usr/bin/env bash
# Boot local DB2 / MySQL / Postgres containers with sample schemas+data,
# then run db-migration-quality-checker against any source→target pair.
#
#   ./scripts/run-local-check.sh                              # default: db2 → mysql
#   ./scripts/run-local-check.sh --source mysql --target postgres
#   ./scripts/run-local-check.sh --source postgres --target db2
#   ./scripts/run-local-check.sh --skip-up                    # assume containers already up
#   ./scripts/run-local-check.sh --down                       # stop containers (keep volumes)
#   ./scripts/run-local-check.sh --down-volumes               # stop + delete volumes
#
# Valid engines: db2, mysql, postgres. Source and target must differ.
# Only the engines involved in the chosen pair are started — so a
# mysql ↔ postgres run skips DB2's multi-minute boot entirely.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker/docker-compose.local.yml"

DB2_CONTAINER="dbqc-db2"
MYSQL_CONTAINER="dbqc-mysql"
POSTGRES_CONTAINER="dbqc-postgres"

DB2_SEED_FILE="/seed/01_schema.sql"

log()  { printf '\033[1;34m[run-local]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[run-local]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[run-local]\033[0m %s\n' "$*" >&2; exit 1; }

compose() { docker compose -f "$COMPOSE_FILE" "$@"; }
require() { command -v "$1" >/dev/null 2>&1 || die "Missing dependency: $1"; }

# ---- Argument parsing ------------------------------------------------------

SOURCE_KIND="db2"
TARGET_KIND="mysql"
SKIP_UP=false
ACTION="run"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --source)         SOURCE_KIND="$2"; shift 2 ;;
        --target)         TARGET_KIND="$2"; shift 2 ;;
        --skip-up)        SKIP_UP=true; shift ;;
        --down)           ACTION="down"; shift ;;
        --down-volumes)   ACTION="down-volumes"; shift ;;
        -h|--help)
            sed -n '2,15p' "$0"
            exit 0
            ;;
        *) die "Unknown arg: $1" ;;
    esac
done

if [[ "$ACTION" == "down" ]]; then
    log "Stopping and removing local stack (volumes preserved)…"
    compose down
    exit 0
fi
if [[ "$ACTION" == "down-volumes" ]]; then
    log "Stopping local stack AND deleting volumes…"
    compose down -v
    exit 0
fi

validate_kind() {
    case "$1" in
        db2|mysql|postgres) ;;
        *) die "Invalid engine: $1 (expected db2, mysql, or postgres)" ;;
    esac
}
validate_kind "$SOURCE_KIND"
validate_kind "$TARGET_KIND"
[[ "$SOURCE_KIND" == "$TARGET_KIND" ]] && die "Source and target must be different engines"

require docker
require java

# ---- Per-engine settings ---------------------------------------------------
# Sets SOURCE_TYPE / SOURCE_JDBC_URL / SOURCE_USERNAME / SOURCE_PASSWORD /
# SOURCE_SCHEMA (or TARGET_* when role==target).

settings_for() {
    local kind="$1" role="$2"   # role = SOURCE or TARGET
    case "$kind" in
        db2)
            printf -v "${role}_TYPE"      'DB2'
            printf -v "${role}_JDBC_URL"  'jdbc:db2://localhost:50000/SOURCEDB'
            printf -v "${role}_USERNAME"  'db2inst1'
            printf -v "${role}_PASSWORD"  'changeme'
            printf -v "${role}_SCHEMA"    'SOURCE_SCHEMA'
            ;;
        mysql)
            printf -v "${role}_TYPE"      'MYSQL'
            printf -v "${role}_JDBC_URL"  'jdbc:mysql://localhost:3306/TARGET_DB'
            printf -v "${role}_USERNAME"  'mysqluser'
            printf -v "${role}_PASSWORD"  'changeme'
            printf -v "${role}_SCHEMA"    'TARGET_DB'
            ;;
        postgres)
            printf -v "${role}_TYPE"      'POSTGRES'
            printf -v "${role}_JDBC_URL"  'jdbc:postgresql://localhost:5432/TARGET_DB'
            printf -v "${role}_USERNAME"  'pguser'
            printf -v "${role}_PASSWORD"  'changeme'
            printf -v "${role}_SCHEMA"    'public'
            ;;
    esac
}

settings_for "$SOURCE_KIND" SOURCE
settings_for "$TARGET_KIND" TARGET

log "Pair: $SOURCE_KIND (source) → $TARGET_KIND (target)"

# ---- Start only the engines this pair needs --------------------------------

services=()
needs_db2=false
needs_mysql=false
needs_postgres=false
case "$SOURCE_KIND $TARGET_KIND" in
    *db2*)      needs_db2=true ;;
esac
case "$SOURCE_KIND $TARGET_KIND" in
    *mysql*)    needs_mysql=true ;;
esac
case "$SOURCE_KIND $TARGET_KIND" in
    *postgres*) needs_postgres=true ;;
esac
$needs_db2       && services+=("db2")
$needs_mysql     && services+=("mysql")
$needs_postgres  && services+=("postgres")

if ! $SKIP_UP; then
    log "Starting: ${services[*]}"
    compose up -d "${services[@]}"
fi

# ---- Wait for each engine to be healthy ------------------------------------

wait_healthy() {
    local name="$1" max_secs="$2" interval="$3"
    local waited=0 status=""
    while (( waited < max_secs )); do
        status=$(docker inspect --format '{{.State.Health.Status}}' "$name" 2>/dev/null || echo starting)
        [[ "$status" == "healthy" ]] && { log "$name is healthy"; return 0; }
        sleep "$interval"
        waited=$(( waited + interval ))
        if (( waited % 60 == 0 )); then
            log "  still waiting for ${name}... (${waited}s, status=$status)"
        fi
    done
    die "$name did not become healthy in ${max_secs}s. Check: docker logs $name"
}

$needs_mysql     && wait_healthy "$MYSQL_CONTAINER"    120  2
$needs_postgres  && wait_healthy "$POSTGRES_CONTAINER" 60   2
$needs_db2       && { log "Waiting for DB2 (first boot: 10–25 min on Apple Silicon under Rosetta)…"; wait_healthy "$DB2_CONTAINER" 1800 15; }

# ---- Idempotent seeding per engine ----------------------------------------

if $needs_mysql; then
    log "Ensuring TARGET_DB + user + grants on MySQL…"
    docker exec -i "$MYSQL_CONTAINER" mysql -uroot -prootpw <<SQL
CREATE DATABASE IF NOT EXISTS TARGET_DB;
CREATE USER IF NOT EXISTS 'mysqluser'@'%' IDENTIFIED BY 'changeme';
ALTER USER 'mysqluser'@'%' IDENTIFIED BY 'changeme';
GRANT ALL PRIVILEGES ON TARGET_DB.* TO 'mysqluser'@'%';
FLUSH PRIVILEGES;
SQL
    seeded=$(docker exec "$MYSQL_CONTAINER" mysql -uroot -prootpw -N -B -e \
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='TARGET_DB' AND table_name='USERS'" 2>/dev/null || echo 0)
    if [[ "$seeded" == "1" ]]; then
        log "MySQL already seeded — skipping"
    else
        log "Seeding MySQL…"
        docker exec -i "$MYSQL_CONTAINER" mysql -uroot -prootpw TARGET_DB \
            < "$ROOT_DIR/docker/seed/mysql/01_schema.sql" \
            || die "MySQL seed failed"
    fi
fi

if $needs_postgres; then
    seeded=$(docker exec "$POSTGRES_CONTAINER" psql -U pguser -d TARGET_DB -tAc \
        "SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_schema)='public' AND lower(table_name)='users'" \
        2>/dev/null || echo 0)
    if [[ "$seeded" == "1" ]]; then
        log "Postgres already seeded — skipping"
    else
        log "Seeding Postgres…"
        docker exec -i "$POSTGRES_CONTAINER" psql -U pguser -d TARGET_DB \
            < "$ROOT_DIR/docker/seed/postgres/01_schema.sql" \
            || die "Postgres seed failed"
    fi
fi

if $needs_db2; then
    already=$(docker exec "$DB2_CONTAINER" su - db2inst1 -c "
        db2 -x connect to SOURCEDB >/dev/null &&
        db2 -x \"select count(*) from syscat.tables where tabschema='SOURCE_SCHEMA' and tabname='USERS'\" |
        tr -d ' '
    " 2>/dev/null || echo "0")
    if [[ "$already" == "1" ]]; then
        log "DB2 already seeded — skipping"
    else
        log "Seeding DB2…"
        docker exec "$DB2_CONTAINER" su - db2inst1 -c "
            db2 connect to SOURCEDB &&
            db2 -tvf $DB2_SEED_FILE
        " || die "DB2 seed failed"
    fi
fi

# ---- Generate data/tables.csv with correct schemas for the chosen pair ----

log "Writing data/tables.csv for $SOURCE_KIND → $TARGET_KIND"
mkdir -p "$ROOT_DIR/data"
cat > "$ROOT_DIR/data/tables.csv" <<CSV
SourceSchema,TargetSchema,TableName,PrimaryKeyName,QueryCondition,IsHexId
${SOURCE_SCHEMA},${TARGET_SCHEMA},USERS,ID,,false
${SOURCE_SCHEMA},${TARGET_SCHEMA},ORDERS,ID,,false
${SOURCE_SCHEMA},${TARGET_SCHEMA},LOOKUP_TABLE,,,
CSV

# ---- Build jar (if missing) and run ----------------------------------------

mkdir -p "$ROOT_DIR/report"
JAR=$(ls -1 "$ROOT_DIR"/target/db-migration-quality-checker-*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1 || true)
if [[ -z "$JAR" ]]; then
    log "No packaged jar — building (./mvnw package -DskipTests)…"
    (cd "$ROOT_DIR" && ./mvnw -q package -DskipTests)
    JAR=$(ls -1 "$ROOT_DIR"/target/db-migration-quality-checker-*.jar | grep -v sources | grep -v javadoc | head -1)
fi
log "Using jar: ${JAR#$ROOT_DIR/}"

log "Running quality checker ($SOURCE_KIND → $TARGET_KIND)…"
cd "$ROOT_DIR"
SOURCE_TYPE="$SOURCE_TYPE" \
SOURCE_JDBC_URL="$SOURCE_JDBC_URL" \
SOURCE_USERNAME="$SOURCE_USERNAME" \
SOURCE_PASSWORD="$SOURCE_PASSWORD" \
TARGET_TYPE="$TARGET_TYPE" \
TARGET_JDBC_URL="$TARGET_JDBC_URL" \
TARGET_USERNAME="$TARGET_USERNAME" \
TARGET_PASSWORD="$TARGET_PASSWORD" \
RANDOM_DATA_COUNT=100 \
java -jar "$JAR"

log "Done. Reports:"
ls -1 "$ROOT_DIR/report" | sed 's/^/  - report\//'
log "Inspect a report, e.g.:  less report/TABLE_ROW_COUNT_COMPARISON_TEST_RESULT.txt"
