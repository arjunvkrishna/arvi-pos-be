-- ============================================================
-- NERAM POS - Tips on bills, tip payouts to staff, monthly expense budgets (V67)
-- Additive only. A tip is recorded when a bill is paid; it is NOT part of sales, GST or the cash drawer.
-- ============================================================
ALTER TABLE bills ADD COLUMN IF NOT EXISTS tip_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS tip_payouts (
    id          BIGSERIAL PRIMARY KEY,
    staff_id    BIGINT        NOT NULL,
    staff_name  VARCHAR(100)  NOT NULL,
    amount      NUMERIC(12,2) NOT NULL,
    paid_on     DATE          NOT NULL,
    note        VARCHAR(200),
    created_by  VARCHAR(100),
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_tip_payouts_staff ON tip_payouts(staff_id);

CREATE TABLE IF NOT EXISTS expense_budgets (
    id             BIGSERIAL PRIMARY KEY,
    category       VARCHAR(40)   NOT NULL UNIQUE,
    monthly_amount NUMERIC(12,2) NOT NULL,
    updated_by     VARCHAR(100),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);
