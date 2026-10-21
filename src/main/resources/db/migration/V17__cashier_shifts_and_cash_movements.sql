-- ============================================================
-- NERAM POS System - Cashier Shifts and Cash Movements
-- Flyway Migration V17 (PostgreSQL Compatible)
-- ============================================================

-- 1. Cashier Shifts
CREATE TABLE IF NOT EXISTS cashier_shifts (
    id BIGSERIAL PRIMARY KEY,
    register_id VARCHAR(100) NOT NULL DEFAULT 'REG-01',
    cashier_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    cashier_name VARCHAR(100) NOT NULL,
    opened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    opening_float NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN', -- OPEN, CLOSED
    closed_at TIMESTAMP,
    closing_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    closing_user_name VARCHAR(100),
    counted_cash NUMERIC(12,2),
    expected_cash NUMERIC(12,2),
    variance NUMERIC(12,2),
    notes TEXT,
    cash_sales NUMERIC(12,2) DEFAULT 0.00,
    upi_sales NUMERIC(12,2) DEFAULT 0.00,
    card_sales NUMERIC(12,2) DEFAULT 0.00,
    cash_refunds NUMERIC(12,2) DEFAULT 0.00,
    upi_refunds NUMERIC(12,2) DEFAULT 0.00,
    card_refunds NUMERIC(12,2) DEFAULT 0.00,
    cash_in_total NUMERIC(12,2) DEFAULT 0.00,
    cash_out_total NUMERIC(12,2) DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Ensure only one open shift exists at any time per register
CREATE UNIQUE INDEX IF NOT EXISTS uq_one_open_shift_per_register 
    ON cashier_shifts (register_id) 
    WHERE status = 'OPEN';

CREATE INDEX IF NOT EXISTS idx_shift_cashier_id ON cashier_shifts(cashier_id);
CREATE INDEX IF NOT EXISTS idx_shift_status ON cashier_shifts(status);

-- 2. Link bills and refunds to cashier shift
ALTER TABLE bills 
    ADD COLUMN IF NOT EXISTS shift_id BIGINT REFERENCES cashier_shifts(id) ON DELETE SET NULL;

ALTER TABLE bill_refunds 
    ADD COLUMN IF NOT EXISTS shift_id BIGINT REFERENCES cashier_shifts(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_bill_shift ON bills(shift_id);
CREATE INDEX IF NOT EXISTS idx_refund_shift ON bill_refunds(shift_id);

-- 3. Cash Movements (Drawer In / Drawer Out)
CREATE TABLE IF NOT EXISTS cash_movements (
    id BIGSERIAL PRIMARY KEY,
    shift_id BIGINT NOT NULL REFERENCES cashier_shifts(id) ON DELETE RESTRICT,
    movement_type VARCHAR(50) NOT NULL, -- CASH_IN, CASH_OUT
    amount NUMERIC(12,2) NOT NULL,
    reason TEXT NOT NULL,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    user_name VARCHAR(100),
    approved_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_movement_shift ON cash_movements(shift_id);
CREATE INDEX IF NOT EXISTS idx_movement_type ON cash_movements(movement_type);
