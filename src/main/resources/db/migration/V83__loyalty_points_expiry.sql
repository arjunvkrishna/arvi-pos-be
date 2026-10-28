-- ============================================================
-- NERAM POS - Loyalty points expiry (V83)
-- Points last as long as the customer keeps buying: every purchase moves the expiry date forward,
-- and the whole balance lapses when a customer has not bought within the validity period.
-- ============================================================

ALTER TABLE customers ADD COLUMN IF NOT EXISTS points_expire_at DATE;

CREATE TABLE IF NOT EXISTS loyalty_settings (
    id BIGINT PRIMARY KEY,
    expiry_days INT NOT NULL DEFAULT 180 CHECK (expiry_days BETWEEN 1 AND 3650),
    min_redeem_points INT NOT NULL DEFAULT 100 CHECK (min_redeem_points BETWEEN 1 AND 100000),
    updated_at TIMESTAMPTZ,
    updated_by_name VARCHAR(100)
);
INSERT INTO loyalty_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

-- Customers who already hold points start their validity period today, so nobody loses points the day this goes live.
UPDATE customers
SET points_expire_at = CURRENT_DATE + (SELECT expiry_days FROM loyalty_settings WHERE id = 1)
WHERE points_balance > 0 AND points_expire_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_customers_points_expire_at ON customers(points_expire_at) WHERE points_balance > 0;
