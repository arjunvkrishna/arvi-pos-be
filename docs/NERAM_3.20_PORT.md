# Neram 3.20 port (backend) and the multi-branch rules it follows

Source: `neram-be-main` at v3.20.2 plus its uncommitted work (loyalty-points expiry, per-user notifications, V81-V83 there).
Base of the earlier port was neram v3.15.0, so this brings in everything neram added after it.

## Migrations

Neram's V71-V83 collided with this project's own V71/V72, so they were renumbered by +2. Order is unchanged.

| Here | What | Neram |
|---|---|---|
| V71, V72 | branch scope + branding, per-branch menu (this project) | - |
| V73 | item modifiers and combos | V71 |
| V74 | customer CRM, loyalty ledger, gift cards, business date | V72 |
| V75 | stock batches and reorder level | V73 |
| V76 | shift scheduling, tip pool | V74 |
| V77 | documents module | V75 |
| V78 | coupons and campaigns | V76 |
| V79 | accounting (P&L), bank reconciliation | V77 |
| V80, V81 | async report exports, retry tracking | V78, V79 |
| V82 | direct-print Wi-Fi names | V80 |
| V83 | loyalty points expiry | V81 |
| V84 | per-user notification state | V82 |
| V85 | split-payment modes fit loyalty points | V83 |
| **V86** | `outlet_id` on tip pools, documents, report jobs; tip pool unique per branch per day | new |
| **V87** | table names unique per branch; `dining_tables.floor`; one open cash drawer per branch | new |

## What the single-cafe code had to change to work here

* **Background work is per cafe.** Report exports no longer poll: a job starts the moment it is submitted, inside that cafe's
  database and the requester's branch scope; one nightly sweep per cafe recovers crashed jobs and purges old files.
  Loyalty expiry (now daily, 04:20) and notification cleanup go through `TenantMaintenanceScheduler`.
* **Caches and push are keyed by cafe.** The menu-version fingerprint is remembered per cafe and branch (and now includes branch
  price overrides); per-user SSE pushes no longer cross cafes that share a username such as `admin`.
* **Public links name the cafe**: `/api/public/{cafe}/gift-cards/{code}/balance`, `/api/public/{cafe}/customers/unsubscribe`,
  `/api/public/{cafe}/campaigns/unsubscribe/{token}`. The old cafe-less URLs are gone.
* **Branch scoping** was added to shift schedules and tip-pool shares (through the employee's branch), and to tip pools,
  documents and report jobs (own `outlet_id`; null = whole cafe).

## Who may do what

```
owner (ADMIN / CAFE_ADMIN)  >  main manager (GENERAL_MANAGER)  >  branch manager (MANAGER)  >  staff
```

* A person creates, changes or deactivates only people **strictly below** them (owners may add owners). This now applies to
  employee records as well as logins (`EmployeeService` used to compare role strings and skipped every check for the newer tiers).
* Payroll follows the same ranks (`PayrollAccess`): nobody opens pay above their own tier.
* **Cafe-wide records** (coupons, campaigns, bank reconciliation, balance sheet, document categories, brand-wide settings) are
  written only by owner and main manager (`PERM_MANAGE_SETTINGS`). **Shared menu** changes (dishes, add-ons, combos) need
  `PERM_MANAGE_MENU`. A branch manager's branch toggles never unlock either.
* Documents: branch users can read cafe-wide documents but not change them; an attachment is only reachable through its document.
* Report files: the requester, or an owner / main manager. A branch manager only ever gets their own branch's figures.
* `GET /api/outlets` returns every branch only to owner and main manager; everyone else sees their own.

`EndpointAccessMatrixTest` checks every endpoint against these tiers using the authorities `CustomUserDetailsService` really
produces. A new endpoint without a `@PreAuthorize` fails the build unless it is listed as open on purpose.

## Known gaps (not changed here)

* The staff toggles `canBill`, `canManageExpenses`, `canViewReports` are not enforced by any endpoint; only order-taking,
  printing and inventory toggles are. Decide whether to wire them or remove them from the staff screen.
* `GET /api/gift-cards` returns Spring Data's `Page` directly (Spring Data 4 warns this shape is not stable).
* OpenPDF stays at 2.0.5: every newer release needs Java 21.
* Bridge firmware: set `OUTLET_CODE` on every bridge of a multi-branch cafe (see the bridge README).
