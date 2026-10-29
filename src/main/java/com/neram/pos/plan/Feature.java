package com.neram.pos.plan;

/**
 * The modules a plan can switch on. Everything not listed here (till, tables, kitchen display, bills, menu and add-ons,
 * basic stock, the analytics dashboard, users, branches, printing) belongs to every plan.
 */
public enum Feature {
    STOCK_CONTROL(Plan.PRO, "Stock counts, expiry and reorder lists"),
    SUPPLIERS_EXPENSES(Plan.PRO, "Suppliers, expenses and budgets"),
    CRM(Plan.PRO, "Customers, loyalty, gift cards and coupons"),
    STAFF_PAYROLL(Plan.PRO, "Staff records, attendance, leave and payroll"),
    TELEGRAM(Plan.PRO, "Telegram alerts and daily reports"),
    EXPORTS(Plan.PRO, "Report exports"),
    CAMPAIGNS(Plan.ENTERPRISE, "Marketing campaigns"),
    SCHEDULING(Plan.ENTERPRISE, "Shift scheduling and tip pool"),
    ACCOUNTING(Plan.ENTERPRISE, "Accounting and bank reconciliation"),
    DOCUMENTS(Plan.ENTERPRISE, "Documents and SOPs"),
    AUDIT(Plan.ENTERPRISE, "Audit log");

    private final Plan minPlan;
    private final String label;

    Feature(Plan minPlan, String label) {
        this.minPlan = minPlan;
        this.label = label;
    }

    /** The cheapest plan that includes this module. */
    public Plan minPlan() {
        return minPlan;
    }

    public String label() {
        return label;
    }
}
