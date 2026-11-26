#!/usr/bin/env bash
# Wipe the local OpenClinica database and let the app rebuild it.
#
# Drops every object in the schema, then restarts OpenClinica so its
# Liquibase migrations recreate the schema and seed data from scratch,
# including the default login root / 12345678 (changed on first login).
#
# ALL DATA IN THE DATABASE IS LOST.
#
# Usage: scripts/reset-db.sh [--yes]
# Env:   PGHOST (127.0.0.1) PGPORT (5432) PGDATABASE (openclinica)
#        PGUSER (clinica) PGPASSWORD (clinica)
#        OC_SERVICE (openclinica, a systemd --user unit) OC_URL
set -euo pipefail

export PGHOST="${PGHOST:-127.0.0.1}" PGPORT="${PGPORT:-5432}"
export PGDATABASE="${PGDATABASE:-openclinica}" PGUSER="${PGUSER:-clinica}"
export PGPASSWORD="${PGPASSWORD:-clinica}"
SERVICE="${OC_SERVICE:-openclinica}"
URL="${OC_URL:-http://localhost:8085/OpenClinica/pages/login/login}"

if [[ "${1:-}" != "--yes" ]]; then
    read -r -p "Erase ALL data in $PGDATABASE on $PGHOST:$PGPORT? [y/N] " answer
    [[ "$answer" =~ ^[Yy]$ ]] || { echo "Aborted."; exit 1; }
fi

echo "Stopping $SERVICE..."
systemctl --user stop "$SERVICE"

echo "Dropping schema public in $PGDATABASE..."
psql -q -v ON_ERROR_STOP=1 <<'SQL'
SET client_min_messages = warning;
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
SQL

echo "Starting $SERVICE (Liquibase rebuilds the schema; this takes a while)..."
systemctl --user start "$SERVICE"
for _ in $(seq 1 120); do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$URL" || true)
    [[ "$code" == 200 ]] && break
    sleep 5
done
if [[ "$code" != 200 ]]; then
    echo "OpenClinica did not come back (HTTP $code). Check: journalctl --user -u $SERVICE" >&2
    exit 1
fi

tables=$(psql -tAc "select count(*) from information_schema.tables where table_schema='public'")
echo "Done: $tables tables. Log in as root / 12345678."
