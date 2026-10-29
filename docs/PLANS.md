# Plans: STARTER, PRO, ENTERPRISE

A cafe's plan (set by the platform owner on the Cafes screen) decides how many branches and staff logins it may have and which
modules are switched on. Everything not listed below (till, tables and floors, kitchen display, bills and refunds, menu, add-ons and
combos, basic stock, the analytics dashboard, owner dashboard, "What sold", users, branches, printing, GST) is in every plan.

| | STARTER | PRO | ENTERPRISE |
|---|---|---|---|
| Branches (default) | 1 | 5 | unlimited |
| Staff logins (active, owner included) | 5 | 25 | unlimited |
| Stock counts, expiry and reorder lists (`STOCK_CONTROL`) | - | yes | yes |
| Suppliers, expenses and budgets (`SUPPLIERS_EXPENSES`) | - | yes | yes |
| Customers, loyalty, gift cards, coupons (`CRM`) | - | yes | yes |
| Staff records, attendance, leave, payroll, tip payouts (`STAFF_PAYROLL`) | - | yes | yes |
| Telegram alerts and daily reports (`TELEGRAM`) | - | yes | yes |
| Report exports, PDF and Excel analytics (`EXPORTS`) | - | yes | yes |
| Marketing campaigns (`CAMPAIGNS`) | - | - | yes |
| Shift scheduling and tip pool (`SCHEDULING`) | - | - | yes |
| Accounting and bank reconciliation (`ACCOUNTING`) | - | - | yes |
| Documents and SOPs (`DOCUMENTS`) | - | - | yes |
| Audit log (`AUDIT`) | - | - | yes |

"Unlimited" is stored as 999. Each plan includes everything the plan below it has.

## How it is enforced

* `@RequiresFeature(Feature.X)` on a controller (or on one endpoint) marks it as part of a module. `PlanGateInterceptor` checks the
  cafe's plan before the controller runs and answers **403** with `{"code":"PLAN_FEATURE","feature":"CRM","requiredPlan":"PRO"}`.
  `@OpenInEveryPlan` exempts one endpoint inside a gated controller (a customer's unsubscribe link must always work).
* `PlanCoverageTest` fails the build when a controller is neither marked nor listed as open on every plan, so a new module cannot
  be added without deciding which plans get it.
* Billing refuses a coupon, gift card or loyalty-points payment on a plan without `CRM`, so those cannot be slipped in through the
  till's API. The Telegram report and the daily close are only booked for cafes whose plan has `TELEGRAM`.
* **Staff logins**: creating a login, or switching an old one back on, is refused when the cafe's active logins reach the plan's
  number. Existing logins are never removed. **Branches**: the cafe's own `max_outlets` (set from the plan; the platform owner may
  give a cafe a different number) is checked when a branch is opened.
* The plan is read from the cafe registry (kept for ten seconds), so a plan change takes effect within seconds and costs no query
  per request. The login and `GET /api/users/me` carry `plan` `{name, maxBranches, maxLogins, features}`; the screens hide what the
  plan lacks and re-read the plan when the app opens, on focus after a few minutes, and whenever the server answers `PLAN_FEATURE`.

## Changing a plan

Platform owner, Cafes screen, **Plan**: picking a plan fills in that plan's branch count (it can still be changed). Moving to a
smaller plan keeps all data and every existing branch and login; it only closes the modules and stops further additions.
Over the API: `PUT /api/super-admin/cafes/{id}` with `subscriptionPlan` (and optionally `maxOutlets`). Without `maxOutlets`, a
changed plan brings that plan's default. A plan name other than the three is refused. An older stored value that is not one of them
counts as PRO so a cafe is never locked out.

## Adding a module

Add a constant to `Feature` with its cheapest plan, mark the controller with `@RequiresFeature`, add the screen's tab rule in the UI
(`auth/tabs.ts`, `inPlan(...)`) and its row to `MODULES` in `features/platform/rules.ts`.
