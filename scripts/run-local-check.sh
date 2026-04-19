#!/usr/bin/env bash
# Boot local DB2 + MySQL containers with sample schemas/data, then run
# db-migration-quality-checker against them and dump reports to ./report.
#
#   ./scripts/run-local-check.sh              # full run: up + seed + checker
#   ./scripts/run-local-check.sh --skip-up    # assume containers already up
#   ./scripts/run-local-check.sh --down       # tear everything down
#
# First boot of the DB2 community image takes 5–10 minutes; later runs reattach
# to the persistent volume and come up in under a minute.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker/docker-compose.local.yml"
DB2_SEED_FILE="/seed/01_schema.sql"

DB2_CONTAINER="dbqc-db2"
MYSQL_CONTAINER="dbqc-mysql"

SOURCE_TYPE="DB2"
SOURCE_JDBC_URL="jdbc:db2://localhost:50000/SOURCEDB"
SOURCE_USERNAME="db2inst1"
SOURCE_PASSWORD="changeme"
TARGET_TYPE="MYSQL"
TARGET_JDBC_URL="jdbc:mysql://localhost:3306/TARGET_DB"
TARGET_USERNAME="mysqluser"
TARGET_PASSWORD="changeme"

log()  { printf '\033[1;34m[run-local]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[run-local]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[run-local]\033[0m %s\n' "$*" >&2; exit 1; }

compose() { docker compose -f "$COMPOSE_FILE" "$@"; }

require() { command -v "$1" >/dev/null 2>&1 || die "Missing dependency: $1"; }

case "${1:-}" in
    --down)
        log "Stopping and removing local stack (volumes preserved)…"
        compose down
        exit 0
        ;;
    --down-volumes)
        log "Stopping local stack AND deleting volumes…"
        compose down -v
        exit 0
        ;;
esac

require docker
require java

SKIP_UP=false
[[ "${1:-}" == "--skip-up" ]] && SKIP_UP=true

if ! $SKIP_UP; then
    log "Starting DB2 + MySQL via docker compose…"
    compose up -d
fi

log "Waiting for MySQL healthcheck…"
for i in $(seq 1 60); do
    status=$(docker inspect --format '{{.State.Health.Status}}' "$MYSQL_CONTAINER" 2>/dev/null || echo "starting")
    [[ "$status" == "healthy" ]] && break
    sleep 2
done
[[ "$status" == "healthy" ]] || die "MySQL did not become healthy in time."
log "MySQL is healthy."

# Self-healing MySQL setup. The /docker-entrypoint-initdb.d scripts only run on
# an empty data dir, so on re-used volumes the DB/user/grants from the first
# boot may mismatch the current compose env. Re-apply everything as root.
log "Ensuring TARGET_DB, mysqluser, and grants exist…"
docker exec -i "$MYSQL_CONTAINER" mysql -uroot -prootpw <<SQL
CREATE DATABASE IF NOT EXISTS TARGET_DB;
CREATE USER IF NOT EXISTS '${TARGET_USERNAME}'@'%' IDENTIFIED BY '${TARGET_PASSWORD}';
ALTER USER '${TARGET_USERNAME}'@'%' IDENTIFIED BY '${TARGET_PASSWORD}';
GRANT ALL PRIVILEGES ON TARGET_DB.* TO '${TARGET_USERNAME}'@'%';
FLUSH PRIVILEGES;
SQL

MYSQL_SEEDED=$(docker exec "$MYSQL_CONTAINER" mysql -uroot -prootpw -N -B -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='TARGET_DB' AND table_name='USERS'" 2>/dev/null || echo 0)
if [[ "$MYSQL_SEEDED" == "1" ]]; then
    log "MySQL TARGET_DB.USERS already exists — skipping seed."
else
    log "Seeding MySQL TARGET_DB…"
    docker exec -i "$MYSQL_CONTAINER" mysql -uroot -prootpw TARGET_DB \
        < "$ROOT_DIR/docker/seed/mysql/01_schema.sql" \
        || die "MySQL seed failed."
fi

log "Waiting for DB2 healthcheck (first boot can take 5–10 minutes)…"
for i in $(seq 1 120); do
    status=$(docker inspect --format '{{.State.Health.Status}}' "$DB2_CONTAINER" 2>/dev/null || echo "starting")
    [[ "$status" == "healthy" ]] && break
    if (( i % 10 == 0 )); then
        log "  still waiting for DB2… ($((i*15))s elapsed, status=$status)"
    fi
    sleep 15
done
[[ "$status" == "healthy" ]] || die "DB2 did not become healthy in time. Check: docker logs $DB2_CONTAINER"
log "DB2 is healthy."

# Idempotent seed: skip if SOURCE_SCHEMA.USERS already exists.
ALREADY_SEEDED=$(docker exec "$DB2_CONTAINER" su - db2inst1 -c "
    db2 -x connect to SOURCEDB >/dev/null &&
    db2 -x \"select count(*) from syscat.tables where tabschema='SOURCE_SCHEMA' and tabname='USERS'\" |
    tr -d ' '
" 2>/dev/null || echo "0")

if [[ "$ALREADY_SEEDED" == "1" ]]; then
    log "DB2 SOURCE_SCHEMA.USERS already exists — skipping seed."
else
    log "Seeding DB2 with $DB2_SEED_FILE …"
    docker exec "$DB2_CONTAINER" su - db2inst1 -c "
        db2 connect to SOURCEDB &&
        db2 -tvf $DB2_SEED_FILE
    " || die "DB2 seed failed. Check: docker logs $DB2_CONTAINER"
fi

# The tool reads SOURCEDB using DB2_USERNAME; db2inst1 owns SOURCE_SCHEMA, so
# that's the account to connect with. If you prefer a dedicated user, create it
# here and GRANT SELECT on SOURCE_SCHEMA.*.

mkdir -p "$ROOT_DIR/report"

JAR=$(ls -1 "$ROOT_DIR"/target/db-migration-quality-checker-*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1 || true)
if [[ -z "$JAR" ]]; then
    log "No packaged jar under target/. Building now (./mvnw package -DskipTests)…"
    (cd "$ROOT_DIR" && ./mvnw -q package -DskipTests)
    JAR=$(ls -1 "$ROOT_DIR"/target/db-migration-quality-checker-*.jar | grep -v sources | grep -v javadoc | head -1)
fi
log "Using jar: ${JAR#$ROOT_DIR/}"

log "Running quality checker…"
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
