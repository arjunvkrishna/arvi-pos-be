# Multi-tenancy: one database per cafe

Every cafe (tenant) gets its **own PostgreSQL database and its own database role** on the same PostgreSQL server.
A bug, a bad migration or a runaway query in one cafe cannot touch another cafe's data, and a cafe's credentials
cannot even open another cafe's database.

```
                        ┌────────────────────────────────────────┐
  one PostgreSQL        │ arvi_control   (registry, never cafe data)
  server                │   tenants           code, db name/user, ENCRYPTED db password, status, plan
                        │   platform_admins   SaaS owner logins
                        │   tenant_events     audit trail (provisioned, suspended, credentials revealed…)
                        ├────────────────────────────────────────┤
                        │ arvi_bluedoor    owner: arvi_bluedoor_app    ← only this role can connect
                        │ arvi_greenleaf   owner: arvi_greenleaf_app   ← only this role can connect
                        │ …one per cafe                                │
                        └────────────────────────────────────────┘
```

Every row in a cafe database also carries `tenant_id` (the cafe's registry id), stamped automatically on insert
(`TenantIdStamper`) and defaulted by the database, so rows always record which restaurant they belong to.

## How a request finds its cafe

| Caller | How the cafe is chosen |
|---|---|
| Cafe staff | Login sends `tenantCode` + username + password. The signed JWT carries the cafe (`tid`, `tc`). **Headers cannot override it.** |
| SaaS owner (`superadmin`) | Login with **no** `tenantCode`. Authenticated against `platform_admins`; the token has `scope=platform`, no cafe, so cafe data is unreachable. |
| Customer QR menu | `GET /api/public/{cafe}/menu` (the UI link is `/menu?cafe=<code>`). |
| Print bridge | Header `X-Tenant-Code: <cafe>` plus that cafe's own `X-Bridge-Token`. |
| Telegram | One bot for all cafes; the link code is `<cafe>_<CODE>`. |
| Background jobs | Run once per cafe through `TenantExecutor` (never against "whatever database is default"). |

If no cafe is selected, any attempt to use cafe data **fails** (`TenantNotSelectedException`, HTTP 400). There is no
default database to fall back to.

## Where the new database's name and password come from (and where they are kept)

Nobody types them. When the SaaS owner creates a cafe (`POST /api/super-admin/cafes`), the app:

1. derives the database name from the cafe code (`bluedoor` → `arvi_bluedoor`, role `arvi_bluedoor_app`);
2. generates a random 32-character password for that role (`SecureRandom`);
3. **encrypts it with AES-256-GCM and stores it in `arvi_control.tenants` before creating anything**, so it can never
   be lost if the process dies midway;
4. runs `CREATE ROLE` / `CREATE DATABASE`, applies the schema (Flyway), creates the cafe's admin, marks the cafe `ACTIVE`.

The only secret that lives in the environment is **one master key**, `TENANT_SECRET_KEY`, set once at install time.
Adding, suspending or re-keying cafes never needs an `.env` edit or a restart: connection pools are built from the
registry the first time a cafe is used.

If provisioning fails, the half-created role/database and registry row are removed; if that cleanup itself fails,
the row is kept as `FAILED` (with its credentials) so an operator can finish it.

## Environment

| Variable | Purpose |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | The **control** database (`CONTROL_DB_*` override each). `DB_NAME` should be e.g. `arvi_control`; it is created on first start if the role may. |
| `TENANT_SECRET_KEY` | **Required.** `openssl rand -base64 32`. Back it up separately from the database: without it the stored cafe passwords cannot be decrypted. |
| `TENANT_SECRET_KEY_PREVIOUS` | Only during key rotation (see below). |
| `PG_ADMIN_USER`, `PG_ADMIN_PASSWORD` | Optional role allowed to `CREATE DATABASE`/`CREATE ROLE` (`CREATEDB CREATEROLE`, not superuser). Blank = use the control role. |
| `TENANT_DB_HOST`, `TENANT_DB_PORT` | Where new cafe databases are created. Blank = same server as the control DB. |
| `TENANT_DB_CONNECTION_LIMIT` | Max connections one cafe's role may hold (default 30), so one busy cafe cannot starve the others. |
| `TENANT_POOL_MAX_SIZE` (6), `TENANT_POOL_EVICT_AFTER_IDLE_MS` (10 min), `TENANT_MAX_OPEN_POOLS` (100) | Per-cafe pool sizing; idle pools are closed and rebuilt on demand. |
| `TENANT_MIGRATE_ON_STARTUP` (true), `TENANT_MIGRATE_PARALLELISM` (4) | Apply new migrations to every cafe at boot. A cafe that fails to migrate is flagged and does not block the others. |
| `PLATFORM_ADMIN_INITIAL_PASSWORD` | Optional password for the first `superadmin`; otherwise a random one is printed once in the log. |
| `SEED_DEMO_DATA` | `true` loads the demo menu into **newly created** cafes. |

## Operating it (no restart needed for any of these)

| Task | Call (platform token) |
|---|---|
| Create a cafe | `POST /api/super-admin/cafes` |
| List / inspect | `GET /api/super-admin/cafes`, `GET /api/super-admin/cafes/{id}` |
| Suspend / reactivate | `PUT /api/super-admin/cafes/{id}` `{"status":"SUSPENDED"}` — takes effect within ~10 s on every replica; existing tokens stop working |
| See DB host/db/user/password (support, restores) | `GET /api/super-admin/cafes/{id}/db-credentials` (audited) |
| Rotate a cafe's DB password | `POST /api/super-admin/cafes/{id}/db-credentials/rotate` — changes it in PostgreSQL, re-encrypts it, rebuilds live pools |
| Print bridge token (per cafe) | `GET …/bridge-token`, `POST …/bridge-token/rotate` |
| Re-run migrations for one cafe | `POST /api/super-admin/cafes/{id}/migrate` |
| Audit trail | `GET /api/super-admin/cafes/{id}/events` |

Deleting a cafe is deliberately **not** exposed over HTTP. To remove one: suspend it, take a `pg_dump` of its
database, then `DROP DATABASE`/`DROP ROLE` by hand and delete its row from `arvi_control.tenants`.

### Rotating `TENANT_SECRET_KEY`

1. Set `TENANT_SECRET_KEY_PREVIOUS` to the old key and `TENANT_SECRET_KEY` to a new one; roll the service.
2. Everything still decrypts (the ciphertext names the key that sealed it).
3. Rotate each cafe's DB password and bridge token (the endpoints above) — this re-encrypts them with the new key.
4. Remove `TENANT_SECRET_KEY_PREVIOUS`.

## Schema changes (new Flyway migration)

Add `db/migration/V<n>__….sql` as usual. On the next start every cafe database is migrated automatically (in parallel,
`TENANT_MIGRATE_PARALLELISM`). In a rolling deploy older replicas may briefly run against a newer schema, so keep
migrations backwards compatible (add columns/tables first; drop or rename in a later release). Platform-level tables
(`tenants`, `platform_admins`) are migrated from `db/control`.

## Capacity notes

* Each cafe has its own connection pool (default max 6, closed after 10 idle minutes). PostgreSQL's default
  `max_connections` is 100: raise it (e.g. 300) or put PgBouncer in front once you pass roughly 50–100 active cafes.
* Background jobs that must visit every cafe (Telegram daily report each minute) keep one connection warm per cafe.
* Because everything lives on one PostgreSQL server, "scale PostgreSQL" means a bigger server, replicas, or moving
  some cafes to another server: set that cafe's `db_host`/`db_port` in the registry after copying its database
  (`pg_dump`/`pg_restore`) and the app connects there on the next pool rebuild.
* Backups: `pg_dump` per cafe database (restore one cafe without touching the others) plus the control database,
  plus the `TENANT_SECRET_KEY`.

## Security properties (and what they rely on)

* Cafe isolation = separate database + separate role + `REVOKE CONNECT … FROM PUBLIC`. A leaked cafe credential
  exposes only that cafe. The control database is also closed to cafe roles.
* The JWT is the only source of the cafe for authenticated calls; tampering breaks its signature.
* The master key protects stored DB passwords against anyone who can read the control database (backups, SQL
  injection into the registry). It does **not** protect against someone who has both the key and the app host.
* The role that creates databases (`PG_ADMIN_*`, or the control role) is the most powerful credential: keep it
  to `CREATEDB CREATEROLE` and do not reuse it for cafes.


---

## Branches (outlets) and the chain of command

A cafe can have several branches. Branch data is **walled off**; the menu is shared.

| Branch-owned (each branch sees only its own) | Shared by the whole cafe |
|---|---|
| bills & bill items/payments/refunds, tables, cash shifts & movements, expenses, recurring-expense templates, inventory & stock moves, stock counts, supplier invoices/payments, staff (employees, attendance, leave, salary, tips), user accounts, notifications, print jobs, printer settings, payment QR (override) | categories, menu items, recipes, promotions, suppliers, budgets, GST, shift/Telegram/daily-close settings, cafe logo & payment QR (default), cafe profile |

How it is enforced (defence in depth, `com.neram.pos.access`):

1. **Request scope.** From the token's user: owner / cafe admin / main manager see the whole cafe, or one branch when the
   UI sends `X-Outlet-Id` ("All branches" = no header). Branch managers and staff are tied to their own branch; the header
   is ignored for them. A user without a branch sees only shared rows.
2. **Hibernate filter** hides other branches' rows from every JPQL/derived query (child tables inherit the rule through
   their parent), and native queries carry the same predicate.
3. **Load/save guards** refuse loading a row of another branch by id, and refuse writing a row for another branch.
4. New rows are stamped with the caller's branch automatically.

### Who can do what

| | Owner / cafe admin | Main manager (GM) | Branch manager | Staff (cashier, captain, kitchen…) |
|---|---|---|---|---|
| Sees | all branches (or picks one) | all branches (or picks one) | own branch | own branch, what toggles allow |
| Create / change / reset / deactivate | everyone below, incl. co-owners | everyone below owner, any branch | staff of own branch | nobody |
| Move staff between branches | yes | yes | no | no |
| Cafe profile: name, tagline, address, GST, logo, public menu, default payment QR | yes | yes | no | no |
| Branch profile, printers, receipt text, branch payment QR | any branch | any branch | own branch | only with the *settings* toggle, own branch |
| Shared menu, recipes, offers | yes | yes | only with the *menu* toggle | only with the *menu* toggle |
| Open / close a branch, add a branch | yes | yes | no | no |
| Grant a toggle to someone | what they hold themselves | what they hold themselves | what they hold themselves | - |

A person can never change their own permissions, and nobody can hand out a right they do not hold.
The rules live in `AccessPolicy`; the platform owner (SaaS super admin) is separate and never sees cafe data.

### Logo and payment QR

`PUT /api/branding/logo` and `PUT /api/branding/payment-qr` (multipart `file`). Add `?outletId=` for a branch override.
PNG/JPEG only (type is detected from the bytes), max 1.5 MB / 4000 px, and a payment QR must contain a readable QR code.
The branch's own QR is used on its bills, otherwise the cafe-wide one, otherwise none. Nothing is shipped with the
backend and nothing is shared between cafes.

### Print bridges in multi-branch cafes

A bridge sends `X-Tenant-Code` and its cafe's token; give each branch's bridge `X-Outlet-Code: <branch code>` so it
only receives that branch's tickets. The server never opens sockets to printers itself in the cloud service
(`PRINTER_DIRECT_ENABLED=false`): a printer address is user input and connecting to it from the server would be a way to
probe the hosting network.
