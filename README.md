# ARVI POS - Backend Service

High-performance, multi-tenant SaaS and multi-franchise Cafe Point of Sale (POS) backend engine built with Java 17, Spring Boot 3, PostgreSQL, and Spring Security.

Designed with enterprise multi-unit restaurant capabilities inspired by Petpooja and Toast POS platforms.

---

## Key Features

### 1. Multi-Tenant SaaS & Multi-Franchise Architecture
- **SaaS Platform Super Admin (`ROLE_SUPER_ADMIN`)**:
  - Global tenant onboarding and lifecycle management (`POST /api/super-admin/cafes`).
  - Tiered subscription plan management (`STARTER`, `PRO`, `ENTERPRISE`).
  - Configurable maximum franchise outlet limits per tenant.
  - Platform-wide aggregate business metrics and revenue analytics (`GET /api/super-admin/analytics`).
- **Brand / Cafe Admin (`ROLE_ADMIN` / `ROLE_CAFE_ADMIN`)**:
  - Centralized menu, pricing, and category management across outlets.
  - Multi-location franchise branch creation and configuration (`POST /api/outlets`).
  - Comparative franchise branch performance and sales analytics (`GET /api/franchise/analytics`).
  - Cafe brand settings and digital logo customization (`GET/PUT /api/tenant/settings`).
- **Multi-Franchise General Manager (`ROLE_GENERAL_MANAGER`)**:
  - Positioned directly under Admin as the multi-outlet operational head.
  - Cross-franchise governance: manages branch managers across different outlets, oversees combined franchise inventory, expense audits, and consolidated P&L reports.
  - Ability to seamlessly switch active outlet context or inspect global franchise data.
- **Single-Outlet Branch Manager (`ROLE_MANAGER`)**:
  - Assigned strictly to a single physical outlet (`outletId`).
  - Scoped exclusively to that branch: manage local outlet staff, register shifts, local stock audits, and branch expense logs without visibility into other franchises.
- **Granular RBAC Staff Permissions (`CASHIER`, `CAPTAIN`, `EMPLOYEE`, `KITCHEN`)**:
  - Dedicated capability checkboxes for fine-grained staff delegation:
    - `canBill`: POS billing, invoice generation, cashier shift management.
    - `canTakeOrders`: Floor captain mode, table assignments, and KOT dispatching.
    - `canManageInventory`: Raw ingredient tracking and inward purchase invoices.
    - `canManageExpenses`: Daily expense vouchers and Petpooja-style petty cash logging.
    - `canViewReports`: Financial reports, revenue dashboards, and P&L analytics.
- **User Profile & Self-Service Password Reset (`/api/users/me`)**:
  - Self-service personal profile management (Full Name, Phone, Email, Address).
  - Secure in-app password reset with existing password verification and length validation (`POST /api/users/me/change-password`).
  - Admin/GM staff password reset capability (`POST /api/users/{id}/reset-password`).

### 2. POS & Restaurant Operations
- **Order Management & Billing**:
  - Dine-in, Takeaway, and Delivery order workflows.
  - Automated GST / VAT tax calculation, itemized discounting, and payment method tracking (Cash, UPI, Card).
  - Bill settlement, receipt reprints, and cancellation audit logs.
- **Kitchen Order Ticket (KOT) Dispatching**:
  - Smart category routing (separate Kitchen Food KOT vs Beverage / Juice KOT).
  - Real-time Server-Sent Events (SSE) notification streams (`/api/bills/stream`).
- **Table Floor Management**:
  - Interactive table status mapping (Available, Occupied, Billed, Reserved).
  - Seamless table merging and order transfer.
- **Shift & Cash Drawer Registers**:
  - Cashier shift opening, floating balance verification, cash drops, and end-of-shift reconciliation reports.
- **Inventory & Supply Chain**:
  - Ingredient and stock level tracking with automated low-stock warnings.
  - Vendor management and inventory transaction logs.
- **Hardware Integration**:
  - Cloud thermal printer bridge polling protocol (`/api/printer/bridge/poll`) with lease claims, idempotency, and automated retry mechanisms for LAN ESC/POS network printers.
- **Public Contactless Menu**:
  - QR-code accessible public menu (`/api/public/menu`) with rate limiting and secure read-only access.

---

## Tech Stack

- **Language & Runtime**: Java 17 (Eclipse Temurin)
- **Framework**: Spring Boot 3.x, Spring Data JPA, Spring Security
- **Authentication**: Stateless JWT (JSON Web Tokens) with refresh token rotation
- **Database**: PostgreSQL 18 with Flyway schema migration versioning
- **Build Tool**: Apache Maven 3.9+
- **Containerization**: Multi-stage minimal Ubuntu / Temurin JRE container with non-root security profile

---

## Project Structure

```
arvi-pos-be/
├── src/
│   ├── main/
│   │   ├── java/com/neram/pos/
│   │   │   ├── config/          # Security, WebMvc, Flyway, and Async configurations
│   │   │   ├── controller/      # REST API Controllers (SuperAdmin, Outlet, Bill, Menu, etc.)
│   │   │   ├── dto/             # Data Transfer Objects & API Request/Response models
│   │   │   ├── entity/          # JPA Domain Entities (Tenant, Outlet, User, Bill, etc.)
│   │   │   ├── repository/      # Spring Data JPA Repositories
│   │   │   ├── security/        # TenantSecurityService, JWT Filter, CustomUserDetailsService
│   │   │   └── service/         # Business logic and transaction management
│   │   └── resources/
│   │       ├── db/control/      # Control database (cafe registry, platform admins) migrations
│   │       ├── db/migration/    # Per-cafe database migrations, applied to every cafe database
│   │       └── application.yml  # Application properties and environment variable bindings
│   └── test/                    # JUnit 5 integration and unit tests
├── Dockerfile                   # Multi-stage optimized JRE Docker build
├── pom.xml                      # Maven dependencies and build plugins
└── .gitignore                   # Git exclusion rules
```

---

## Configuration & Environment Variables

All sensitive values are injected strictly through environment variables:

| Variable | Description | Default / Example |
|---|---|---|
| `DB_HOST` | PostgreSQL host (control database; cafe databases are created on the same server) | `postgres` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | **Control** database (cafe registry + platform admins). Never holds cafe data | `arvi_control` |
| `DB_USER` | Control database role (needs `CREATEDB CREATEROLE`, or set `PG_ADMIN_USER`) | `arvi_user` |
| `DB_PASSWORD` | Control database role password | `<your_db_password>` |
| `TENANT_SECRET_KEY` | Master key (base64, 32 bytes) that encrypts per-cafe DB passwords. **Required.** `openssl rand -base64 32` | `<generated>` |
| `JWT_SECRET` | 256-bit secret key for HMAC-SHA512 signing | `<your_jwt_secret_key>` |
| `JWT_EXPIRATION` | Token validity duration in milliseconds | `86400000` (24h) |
| `PRINTER_BRIDGE_TOKEN` | Shared secret token for ESP32 print bridge | `<your_bridge_token>` |
| `SEED_DEMO_DATA` | Flag to seed demo data on fresh startup | `true` or `false` |
| `SEED_DEMO_USERS` | Flag to seed demo users on fresh startup | `true` or `false` |
| `JAVA_OPTS` | JVM heap and memory optimization flags | `-Xms128m -Xmx512m` |

---

## Data isolation: one database per cafe

Each cafe has its own PostgreSQL database and role; cafe databases are created, migrated and registered at runtime
from the SaaS console (`POST /api/super-admin/cafes`), with generated credentials stored AES-GCM encrypted in the
control database. No `.env` edit or restart is needed to add a cafe. Staff log in with a **cafe code**; the
`superadmin` logs in without one. See [docs/MULTI_TENANCY.md](docs/MULTI_TENANCY.md) for the full design, the
operations guide (rotate credentials, suspend, migrate) and capacity notes.

---

## Branches, roles and branding

Branch data (bills, tables, shifts, expenses, stock, staff, printers) is separated per outlet; the menu is shared.
Owner > main manager > branch manager > staff, enforced in the backend, with delegable toggles for menu and settings.
Every cafe uploads its own logo and payment QR (cafe-wide, with an optional per-branch QR). Details and the permission
table: [docs/MULTI_TENANCY.md](docs/MULTI_TENANCY.md#branches-outlets-and-the-chain-of-command).

---

## Getting Started

### Local Development (Maven)

Ensure PostgreSQL is running locally, then execute:

```bash
# Package and verify tests
mvn clean test

# Run Spring Boot application locally
mvn spring-boot:run
```

The application will start on `http://localhost:8080`.

### Docker Build

```bash
# Build Docker image
docker build -t arjunvk/arvi-backend:v1.0.0 .

# Run container
docker run -d \
  -p 8080:8080 \
  --name arvi-backend \
  --env-file .env \
  arjunvk/arvi-backend:v1.0.0
```

---

## Docker Swarm Horizontal Scalability & Traffic Spike Management

The backend is stateless and optimized for horizontal scaling in Docker Swarm mode:
- **Stateless Replicas**: JWT token authentication with database-backed token rotation (`SELECT ... FOR UPDATE` row locks).
- **Concurrency Pooling**: Externalized HikariCP pool (`HIKARI_MAX_POOL_SIZE`, `HIKARI_MIN_IDLE`) and Tomcat thread configuration (`TOMCAT_MAX_THREADS`, `TOMCAT_MIN_SPARE_THREADS`).
- **Flyway Lock Retry**: Multiple replicas starting concurrently wait on each cafe database's migrations gracefully (`lock-retry-count: 60`).
- **Per-cafe pools**: connection pools are created lazily per cafe and closed when idle (see `docs/MULTI_TENANCY.md` for sizing and `max_connections`).
- **Graceful Shutdown**: 30-second shutdown phase timeout (`server.shutdown: graceful`) prevents dropped transactions during scale-in operations.

```bash
# Scale backend dynamically during rush hours
docker service scale arvi_backend=10
```

---

## Health & Verification Endpoints

- **Health Check**: `GET /api/health`
- **Authentication**: `POST /api/auth/login` (`{tenantCode, username, password}`; omit `tenantCode` for the platform `superadmin`)
- **Public menu**: `GET /api/public/{cafe}/menu`
- **SaaS Cafes**: `GET /api/super-admin/cafes`
- **Franchise Outlets**: `GET /api/outlets`
- **Franchise Analytics**: `GET /api/franchise/analytics`
