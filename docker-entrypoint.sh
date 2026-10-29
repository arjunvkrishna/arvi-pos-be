#!/bin/sh
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
exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar
