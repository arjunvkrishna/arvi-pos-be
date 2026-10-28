-- ============================================================
-- NERAM POS - Customer CRM, Loyalty Ledger, Gift Cards & Business Date (V74)
-- ============================================================

CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(20) UNIQUE NOT NULL,
    email VARCHAR(150),
    birthday DATE,
    anniversary DATE,
    address TEXT,
    allergies TEXT,
    notes TEXT,
    marketing_consent BOOLEAN NOT NULL DEFAULT FALSE,
    unsubscribe_token VARCHAR(64) UNIQUE NOT NULL DEFAULT replace(gen_random_uuid()::text, '-', ''),
    points_balance INT NOT NULL DEFAULT 0 CHECK (points_balance >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS business_date DATE;

-- Historical business_date backfill using local IST timestamp with 4 AM cutoff
UPDATE bills SET business_date = (created_at - INTERVAL '4 hours')::date
WHERE business_date IS NULL;

-- Database default backstop for zero billing disruption
ALTER TABLE bills ALTER COLUMN business_date SET DEFAULT ((CURRENT_TIMESTAMP - INTERVAL '4 hours')::date);
ALTER TABLE bills ALTER COLUMN business_date SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_bills_customer_id ON bills(customer_id);
CREATE INDEX IF NOT EXISTS idx_bills_business_date ON bills(business_date);

CREATE TABLE loyalty_ledger (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    bill_id BIGINT REFERENCES bills(id) ON DELETE SET NULL,
    type VARCHAR(20) NOT NULL, -- EARN, REDEEM, REFUND, ADJUST
    points INT NOT NULL,
    balance_after INT NOT NULL,
    note TEXT,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_loyalty_customer ON loyalty_ledger(customer_id);

CREATE TABLE gift_cards (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(32) UNIQUE NOT NULL,
    initial_balance NUMERIC(10,2) NOT NULL,
    current_balance NUMERIC(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, EXHAUSTED, VOIDED
    payment_mode VARCHAR(50) NOT NULL,            -- Explicit mode: CASH, UPI, CARD
    issued_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    issued_to_customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    expires_at DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_gift_card_balance CHECK (current_balance >= 0)
);

CREATE TABLE gift_card_transactions (
    id BIGSERIAL PRIMARY KEY,
    gift_card_id BIGINT NOT NULL REFERENCES gift_cards(id),
    bill_id BIGINT REFERENCES bills(id) ON DELETE SET NULL,
    amount NUMERIC(10,2) NOT NULL,
    balance_after NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_gift_card_tx_card ON gift_card_transactions(gift_card_id);

ALTER TABLE cashier_shifts
    ADD COLUMN IF NOT EXISTS gift_card_cash_sales NUMERIC(12,2) NOT NULL DEFAULT 0;
