#!/usr/bin/env bash
# Dumps the control database and every cafe database to ./backups/<date>/, keeping the last 7 days.
# Not scheduled by default. To run it nightly:  (crontab -l; echo "30 2 * * * $PWD/backup.sh") | crontab -
set -euo pipefail
umask 077
cd "$(dirname "$0")"
db=$(docker ps --filter "name=^${STACK:-arvi}_db\." --format '{{.ID}}' | head -1)
[ -n "$db" ] || { echo "The database container is not running here." >&2; exit 1; }
out="backups/$(date +%F)"
mkdir -p "$out"; chmod 700 backups
for name in $(docker exec "$db" psql -U arvi_user -d postgres -Atc "select datname from pg_database where datname like 'arvi\_%'"); do
  docker exec "$db" pg_dump -U arvi_user -Fc "$name" > "$out/$name.dump"
done
find backups -mindepth 1 -maxdepth 1 -type d -mtime +7 -exec rm -rf {} +
echo "Backed up to $PWD/$out"
echo "Also keep a copy of secrets/arvi_tenant_secret_key somewhere else: without it the dumps cannot be used."
