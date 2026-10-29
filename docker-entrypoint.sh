#!/bin/bash
# Starts the app. Any secret may be given as a file (Docker/Swarm secrets are mounted under /run/secrets) by setting
# <NAME>_FILE instead of <NAME>, so passwords never appear in `docker service inspect` or `docker inspect`.
set -e
for name in DB_PASSWORD CONTROL_DB_PASSWORD PG_ADMIN_PASSWORD TENANT_SECRET_KEY TENANT_SECRET_KEY_PREVIOUS JWT_SECRET \
            PLATFORM_ADMIN_INITIAL_PASSWORD TELEGRAM_BOT_TOKEN TELEGRAM_WEBHOOK_SECRET PRINTER_BRIDGE_TOKEN; do
  file=$(printenv "${name}_FILE" || true)
  [ -n "$file" ] || continue
  if [ ! -r "$file" ]; then
    echo "${name}_FILE points to $file, which cannot be read" >&2
    exit 1
  fi
  value=$(cat "$file")
  export "$name=$value"
done
# On a fresh start (or after a reboot) the database may still be coming up, and in Swarm its name does not resolve until its task
# runs. Wait for it instead of crashing with a long stack trace and being restarted. After two minutes carry on, so a real
# problem is reported by the app itself.
db_host="${CONTROL_DB_HOST:-${DB_HOST:-}}"
db_port="${CONTROL_DB_PORT:-${DB_PORT:-5432}}"
if [ -n "$db_host" ]; then
  echo "Waiting for the database at ${db_host}:${db_port} ..."
  for _ in $(seq 1 60); do
    if (exec 3<>"/dev/tcp/${db_host}/${db_port}") 2>/dev/null; then
      echo "Database is reachable."
      break
    fi
    sleep 2
  done
fi
exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar
