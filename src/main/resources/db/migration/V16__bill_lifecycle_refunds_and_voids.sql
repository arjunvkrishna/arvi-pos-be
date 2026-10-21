-- ============================================================
-- NERAM POS System - Auditable Voids and Refunds
-- Flyway Migration V16 (PostgreSQL Compatible)
-- ============================================================

-- 1. Void audit fields on bills
ALTER TABLE bills 
    ADD COLUMN IF NOT EXISTS voided_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS voided_by_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS void_reason TEXT;

-- 2. Bill Refunds Table
CREATE TABLE IF NOT EXISTS bill_refunds (
    id BIGSERIAL PRIMARY KEY,
    bill_id BIGINT NOT NULL REFERENCES bills(id) ON DELETE RESTRICT,
    amount NUMERIC(12,2) NOT NULL,
    payment_mode VARCHAR(50) NOT NULL DEFAULT 'CASH', -- CASH, UPI, CARD
    reason TEXT NOT NULL,
    refunded_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    refunded_by_name VARCHAR(100),
    refunded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'SUCCESS', -- SUCCESS, PENDING, FAILED
    idempotency_key VARCHAR(150) UNIQUE,
    external_reference VARCHAR(150),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refund_bill_id ON bill_refunds(bill_id);
CREATE INDEX IF NOT EXISTS idx_refund_idempotency ON bill_refunds(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_refund_created_at ON bill_refunds(created_at);
