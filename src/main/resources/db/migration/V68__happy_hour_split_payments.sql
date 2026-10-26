-- ============================================================
-- NERAM POS - Recurring promotions (happy hour) and split payments (V68)
-- Additive only. Existing promotions stay one-off date ranges; existing bills keep their single payment mode.
-- ============================================================
ALTER TABLE promotions
    ADD COLUMN IF NOT EXISTS recurring    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS days_of_week VARCHAR(20),
    ADD COLUMN IF NOT EXISTS daily_start  TIME,
    ADD COLUMN IF NOT EXISTS daily_end    TIME;

CREATE TABLE IF NOT EXISTS bill_payments (
    id         BIGSERIAL PRIMARY KEY,
    bill_id    BIGINT        NOT NULL REFERENCES bills(id) ON DELETE CASCADE,
    mode       VARCHAR(10)   NOT NULL,
    amount     NUMERIC(12,2) NOT NULL,
    reference  VARCHAR(100),
    created_at TIMESTAMP     NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_bill_payments_bill ON bill_payments(bill_id);

ALTER TABLE bills ADD COLUMN IF NOT EXISTS payment_summary VARCHAR(150);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    idem_key   VARCHAR(80) PRIMARY KEY,
    bill_id    BIGINT      NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
);
