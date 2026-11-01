#!/usr/bin/env bash
# Starts a project-local PostgreSQL 17 instance for development and verification.
# Docker Compose remains the primary setup; this script is the fallback for machines
# without Docker. Binaries are downloaded from Maven Central into "ResourceSwapHub database"/.local.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOCAL_DIR="$ROOT_DIR/ResourceSwapHub database/.local"
PG_VERSION="${BOOKBRIDGE_PG_VERSION:-17.10.0}"
PORT="${BOOKBRIDGE_DB_PORT:-55432}"
DB_NAME="${BOOKBRIDGE_DB_NAME:-bookbridge}"
DB_USER="${BOOKBRIDGE_DB_USER:-bookbridge}"
DB_PASSWORD="${BOOKBRIDGE_DB_PASSWORD:-bookbridge_dev}"
PG_HOME="$LOCAL_DIR/pg17"
PG_DATA="$LOCAL_DIR/pgdata"
LOG_FILE="$LOCAL_DIR/postgres.log"
JDBC_VERSION="42.7.14"
JDBC_JAR="$LOCAL_DIR/postgresql-$JDBC_VERSION.jar"
TOOL="$ROOT_DIR/ResourceSwapHub database/scripts/DatabaseTool.java"

case "$(uname -s)" in
  Darwin) OS="darwin" ;;
  Linux) OS="linux" ;;
  *) echo "Unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac

case "$(uname -m)" in
  arm64|aarch64) ARCH="arm64v8" ;;
  x86_64|amd64) ARCH="amd64" ;;
  *) echo "Unsupported architecture: $(uname -m)" >&2; exit 1 ;;
esac

PLATFORM="$OS-$ARCH"
JAR="$LOCAL_DIR/postgres-$PLATFORM.jar"
ARTIFACT="embedded-postgres-binaries-$PLATFORM"
JAR_URL="https://repo.maven.apache.org/maven2/io/zonky/test/postgres/$ARTIFACT/$PG_VERSION/$ARTIFACT-$PG_VERSION.jar"

ensure_binaries() {
  if [[ -x "$PG_HOME/bin/pg_ctl" ]]; then
    return
  fi
  mkdir -p "$LOCAL_DIR"
  echo "Downloading PostgreSQL $PG_VERSION ($PLATFORM)…"
  curl -fSL --retry 3 -o "$JAR" "$JAR_URL"
  mkdir -p "$LOCAL_DIR/extract"
  unzip -o -q "$JAR" -d "$LOCAL_DIR/extract"
  local archive
  archive="$(find "$LOCAL_DIR/extract" -name 'postgres-*.txz' | head -1)"
  if [[ -z "$archive" ]]; then
    echo "Could not find PostgreSQL archive in $JAR" >&2
    exit 1
  fi
  rm -rf "$PG_HOME"
  mkdir -p "$PG_HOME"
  tar -xJf "$archive" -C "$PG_HOME"
  rm -rf "$LOCAL_DIR/extract"
}

ensure_jdbc() {
  if [[ -f "$JDBC_JAR" ]]; then
    return
  fi
  mkdir -p "$LOCAL_DIR"
  echo "Downloading PostgreSQL JDBC driver…"
  curl -fSL --retry 3 -o "$JDBC_JAR" \
    "https://repo.maven.apache.org/maven2/org/postgresql/postgresql/$JDBC_VERSION/postgresql-$JDBC_VERSION.jar"
}

run_tool() {
  ensure_jdbc
  java -cp "$JDBC_JAR" "$TOOL" "$@"
}

ensure_data() {
  if [[ -f "$PG_DATA/PG_VERSION" ]]; then
    return
  fi
  echo "Initializing PostgreSQL data directory…"
  mkdir -p "$PG_DATA"
  "$PG_HOME/bin/initdb" \
    -D "$PG_DATA" \
    -U "$DB_USER" \
    --encoding=UTF8 \
    --locale=C \
    --auth-local=trust \
    --auth-host=trust >/dev/null
  "$PG_HOME/bin/pg_ctl" -D "$PG_DATA" -l "$LOG_FILE" -o "-p $PORT -h 127.0.0.1" start >/dev/null
  sleep 1
  BOOKBRIDGE_DB_HOST=127.0.0.1 \
  BOOKBRIDGE_DB_PORT="$PORT" \
  BOOKBRIDGE_DB_NAME="$DB_NAME" \
  BOOKBRIDGE_DB_USER="$DB_USER" \
  BOOKBRIDGE_DB_PASSWORD="$DB_PASSWORD" \
    run_tool bootstrap
  "$PG_HOME/bin/pg_ctl" -D "$PG_DATA" stop >/dev/null
  printf "host all all 127.0.0.1/32 scram-sha-256\nlocal all all trust\n" > "$PG_DATA/pg_hba.conf"
}

start() {
  ensure_binaries
  ensure_data
  if "$PG_HOME/bin/pg_ctl" -D "$PG_DATA" status >/dev/null 2>&1; then
    echo "PostgreSQL is already running on port $PORT."
  else
    "$PG_HOME/bin/pg_ctl" -D "$PG_DATA" -l "$LOG_FILE" -o "-p $PORT -h 127.0.0.1" start >/dev/null
    echo "PostgreSQL started on 127.0.0.1:$PORT."
  fi
  echo "  database: $DB_NAME"
  echo "  user:     $DB_USER"
  echo "  password: $DB_PASSWORD"
  echo "  url:      jdbc:postgresql://127.0.0.1:$PORT/$DB_NAME"
}

stop() {
  ensure_binaries
  if "$PG_HOME/bin/pg_ctl" -D "$PG_DATA" status >/dev/null 2>&1; then
    "$PG_HOME/bin/pg_ctl" -D "$PG_DATA" stop >/dev/null
    echo "PostgreSQL stopped."
  else
    echo "PostgreSQL is not running."
  fi
}

reset() {
  ensure_binaries
  stop || true
  rm -rf "$PG_DATA"
  echo "PostgreSQL data directory removed. Run '$0 start' to recreate it."
}

status() {
  ensure_binaries
  "$PG_HOME/bin/pg_ctl" -D "$PG_DATA" status || true
}

seed() {
  start
  local which="${1:-all}"
  BOOKBRIDGE_DB_HOST=127.0.0.1 \
  BOOKBRIDGE_DB_PORT="$PORT" \
  BOOKBRIDGE_DB_NAME="$DB_NAME" \
  BOOKBRIDGE_DB_USER="$DB_USER" \
  BOOKBRIDGE_DB_PASSWORD="$DB_PASSWORD" \
    run_tool run \
      "$ROOT_DIR/ResourceSwapHub database/seeds/reference/catalogue.sql" \
      $(if [[ "$which" != "reference" ]]; then echo "$ROOT_DIR/ResourceSwapHub database/seeds/development/demo.sql"; fi)
}

case "${1:-start}" in
  start) start ;;
  stop) stop ;;
  reset) reset ;;
  status) status ;;
  seed) shift; seed "$@" ;;
  *) echo "Usage: $0 {start|stop|reset|status|seed [reference|all]}" >&2; exit 1 ;;
esac
