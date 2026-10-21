-- ============================================================
-- NERAM POS System - Drawer Analysis, Cash Transfers & Auditability
-- Flyway Migration V44 (PostgreSQL Compatible)
-- ============================================================

-- 1. Extend cash_movements with transfer categorization, destination, and reference fields
ALTER TABLE cash_movements
    ADD COLUMN IF NOT EXISTS transfer_type VARCHAR(50) DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS destination_account VARCHAR(150),
    ADD COLUMN IF NOT EXISTS reference_number VARCHAR(150);

CREATE INDEX IF NOT EXISTS idx_movement_transfer_type ON cash_movements(transfer_type);

-- 2. Extend cashier_shifts with transfers to/from bank/safe, handover reference, and explicit correction audit trail
ALTER TABLE cashier_shifts
    ADD COLUMN IF NOT EXISTS transfers_out_total NUMERIC(12,2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS transfers_in_total NUMERIC(12,2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS previous_shift_id BIGINT REFERENCES cashier_shifts(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS carry_forward_ref VARCHAR(150),
    ADD COLUMN IF NOT EXISTS is_corrected BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS corrected_counted_cash NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS corrected_variance NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS correction_reason TEXT,
    ADD COLUMN IF NOT EXISTS corrected_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS corrected_by_user_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS corrected_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_shift_opened_at ON cashier_shifts(opened_at);
CREATE INDEX IF NOT EXISTS idx_shift_closed_at ON cashier_shifts(closed_at);
CREATE INDEX IF NOT EXISTS idx_shift_prev_id ON cashier_shifts(previous_shift_id);

-- 3. Extend shift_settings with configurable anomaly threshold parameters
ALTER TABLE shift_settings
    ADD COLUMN IF NOT EXISTS variance_threshold NUMERIC(12,2) DEFAULT 500.00,
    ADD COLUMN IF NOT EXISTS large_movement_threshold NUMERIC(12,2) DEFAULT 5000.00,
    ADD COLUMN IF NOT EXISTS flag_missing_count BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS flag_transfers_without_ref BOOLEAN DEFAULT TRUE;
