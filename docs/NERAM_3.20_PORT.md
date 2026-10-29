# Neram 3.20 port (backend) and the multi-branch rules it follows

Source: `neram-be-main` up to v3.21.0 (loyalty-points expiry, per-user notifications, V81-V83 there, and the 3.21 "What sold" report).
Base of the earlier port was neram v3.15.0, so this brings in everything neram added after it. The web app in `arvi-pos-ui` is
neram's React app (same version) with a cafe/branch layer on top, so the two are meant to be taken together.

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
| **V88** | `users.can_cancel_order` (existing order-takers keep the ability); unused bill/expenses/reports toggles unmapped | new |

## Added on top of the neram code

* **Kitchen display** (`GET /api/kitchen/tickets`): the open kitchen tickets with dish, quantity, notes and table, and **no prices or
  customer details**. Kitchen staff open it instead of the bill list; it follows the same branch scope as everything else.
* **What sold** (`GET /api/analytics/items-sold`, neram 3.21): dishes sold per day or week with search, category and order-type
  filters. Owner, main manager and branch manager only; a branch manager's figures are their own branch (the bill items carry the
  branch filter).
* **Platform cafe counts** (`SuperAdminService`) are read with plain JDBC per cafe database. Counting through JPA reused one
  session across cafes inside a request and reported the first cafe's numbers for all of them.

## What the single-cafe code had to change to work here

* **Background work is per cafe.** Report exports no longer poll: a job starts the moment it is submitted, inside that cafe's
  database and the requester's branch scope; one nightly sweep per cafe recovers crashed jobs and purges old files.
  Loyalty expiry (now daily, 04:20) and notification cleanup go through `TenantMaintenanceScheduler`.
* **Timed reports are booked, not polled.** The Telegram low-stock report and the end-of-day close used to wake every minute and
  look inside every cafe's database. `TimedCafeJobs` reads each cafe's two times once and books a trigger for exactly that
  minute (in the cafe's own time zone); nothing touches the cafe's database until it fires. Saving either setting re-books
  that cafe at once, and a refresh every 30 minutes (`app.jobs.schedule-refresh-ms`) picks up cafes added, suspended or
  changed by another process. Telegram is used only for these reports and for linking managers; there is no customer/CRM use of it.
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
* **An owner or main manager works in every branch even though their login row stores one home branch** (every cafe migrated from
  a single-cafe install has its owner on branch 1). Their login, and their own staff record, therefore stay visible and loadable
  whichever branch they are looking at (`OutletScoped.staysInEveryBranch`). Before this, switching an owner to a second branch
  broke `/users/me` and notifications, and bills they raised there lost their "created by".
* **Cancelling an order is its own permission** (`PERM_CANCEL_ORDER`, the "can cancel order" toggle). Taking orders no longer
  includes it. It covers cancel, void and the CANCELLED kitchen ticket for orders that are not yet paid; paid bills still go
  through the refund workflow. Owners and managers always have it. V88 gives it to everyone who could cancel before, so
  nothing changes on deploy; a manager can then switch it off per person. The old "can bill / expenses / reports" toggles were
  never checked by any endpoint and are gone from the API.

`EndpointAccessMatrixTest` checks every endpoint against these tiers using the authorities `CustomUserDetailsService` really
produces. A new endpoint without a `@PreAuthorize` fails the build unless it is listed as open on purpose.

## Known gaps (not changed here)

* A browser closing a live-updates stream (tab closed, page changed) used to be logged as a critical error; it is now a debug line.
* `GET /api/gift-cards` returns Spring Data's `Page` directly (Spring Data 4 warns this shape is not stable).
* OpenPDF stays at 2.0.5: every newer release needs Java 21.
* Bridge firmware: set `OUTLET_CODE` on every bridge of a multi-branch cafe (see the bridge README).
