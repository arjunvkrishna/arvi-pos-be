#!/usr/bin/env bash
# Installs or updates ARVI POS on this Docker Swarm node. Safe to run again.
#   ARVI_VERSION   image tag to run (default 3.0.3)       WEB_PORT   public port (default 80)
#   PUBLIC_URL     address people type in the browser, e.g. http://1.2.3.4 (comma-separated for several). Sign-in is
#                  refused from any other address, so change it here when a domain is added.   STACK   stack name (default arvi)
set -euo pipefail
cd "$(dirname "$0")"
# Settings that are not secret live in ./.env (ARVI_VERSION, WEB_PORT, PUBLIC_URL, STACK); anything already in the environment wins.
if [ -f .env ]; then
  while IFS='=' read -r key value; do
    case "$key" in ''|\#*) continue ;; esac
    [ -n "${!key+x}" ] || export "$key=$value"
  done < .env
fi
export ARVI_VERSION="${ARVI_VERSION:-3.0.3}" WEB_PORT="${WEB_PORT:-80}" STACK="${STACK:-arvi}"
export PUBLIC_URL="${PUBLIC_URL:-*}"

for image in "arvi-backend:$ARVI_VERSION" "arvi-ui:$ARVI_VERSION"; do
  docker image inspect "$image" >/dev/null 2>&1 || { echo "Image $image is not loaded on this machine (see README.md)." >&2; exit 1; }
done

if [ "$(docker info --format '{{.Swarm.LocalNodeState}}')" != "active" ]; then
  docker swarm init --advertise-addr "${ADVERTISE_ADDR:-$(hostname -I | awk '{print $1}')}" >/dev/null
  echo "Swarm initialised on this node."
fi

umask 077
mkdir -p secrets
# A secret is created once and then never changes. A copy is kept in ./secrets because Swarm will not show it again.
make_secret() {
  local name="$1"; shift
  docker secret inspect "$name" >/dev/null 2>&1 && return 0
  [ -s "secrets/$name" ] || "$@" > "secrets/$name"
  docker secret create "$name" "secrets/$name" >/dev/null
  echo "Created secret $name"
}
strong_password() { echo "$(openssl rand -hex 8)-Aa$(openssl rand -hex 4)!"; }
make_secret arvi_db_password openssl rand -hex 24
make_secret arvi_tenant_secret_key openssl rand -base64 32
make_secret arvi_jwt_secret openssl rand -hex 48
make_secret arvi_platform_admin_password strong_password

docker stack deploy -c docker-stack.yml --resolve-image never --detach=true "$STACK"
echo "Deployed stack '$STACK' (version $ARVI_VERSION). Watch it with:  docker stack ps $STACK"
