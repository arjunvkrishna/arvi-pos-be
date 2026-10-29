# ARVI POS on Docker Swarm

Three services on one node: `db` (PostgreSQL 18, one database per cafe plus the control database), `backend` (Spring Boot) and
`web` (nginx with the React app, which proxies `/api` to `backend`). The web service is the only thing published (port 80).

## First install

1. Build and load the images on the node (nothing is pulled from a registry; PostgreSQL is pulled from Docker Hub):

   ```bash
   # on your machine, from the backend and UI repositories
   docker build -t arvi-backend:3.0.1 .          # in arvi-pos-be
   docker build -t arvi-ui:3.0.1 .               # in arvi-pos-ui
   docker save arvi-backend:3.0.1 arvi-ui:3.0.1 | gzip | ssh <node> 'gunzip | docker load'
   ```

2. Copy this folder to the node and run it:

   ```bash
   printf 'PUBLIC_URL=http://<node address>\n' > .env     # the address people type in the browser; kept for later updates
   ./deploy.sh
   ```

   `deploy.sh` starts Swarm if needed, creates the four secrets (database password, `TENANT_SECRET_KEY`, JWT secret and the
   platform owner's password) once, keeps a copy of each in `./secrets/` (Swarm never shows a secret again) and deploys the stack.

3. Sign in as `superadmin` (the platform screen). The password is in `./secrets/arvi_platform_admin_password`. Create the first
   cafe there; it gets its own database and its own owner login.

## Updating

Load the new images with a new tag, then `ARVI_VERSION=<tag> ./deploy.sh`. The backend restarts after the database; migrations run
on start (every cafe, in parallel; a cafe that fails is flagged and does not block the rest). One copy of each service runs at a time.

## What matters

* **Every password the app uses is a Docker secret** (`docker secret ls`); no service has one in its environment. `deploy.sh` also writes
  nothing else: for your own reference keep a private `credentials.env` next to the stack with the same values (never commit it).

* **`secrets/arvi_tenant_secret_key` must be kept somewhere else too.** The cafes' database passwords are stored encrypted with it;
  without it a restored database cannot be opened.
* **Back up the databases.** `./backup.sh` dumps the control database and every cafe database to `./backups/<date>/` and keeps seven
  days. It is not scheduled until you add the cron line shown in the script. Copy the dumps off the machine.
* Data lives in the named volumes `arvi_pgdata` and `arvi_logs` (`docker volume ls`); removing the stack does not delete them.
* **`PUBLIC_URL` must be the address people type in the browser** (comma-separated if there are several, e.g. the IP and a domain).
  The backend refuses sign-in from any other address with `403 Invalid CORS request`. After adding a domain, edit `.env` and run `./deploy.sh` again.
* The site is plain HTTP on the port above. Put a domain and TLS in front (a load balancer, or Caddy/Traefik in this stack) before real
  customers use it; logins and tokens travel in clear text over HTTP.
* The Telegram bot is optional. Set `TELEGRAM_BOT_TOKEN_FILE` (and a secret) on `backend` only if you want the daily reports by Telegram.

## Handy commands

```bash
docker stack services arvi                 # are all three up (1/1)?
docker service logs -f arvi_backend        # application log
docker stack ps arvi --no-trunc            # why a task restarted
docker stack rm arvi                       # stop everything (volumes and secrets stay)
```
