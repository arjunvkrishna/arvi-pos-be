-- ============================================================
-- NERAM POS System - Add printed_by column to bills table
-- Flyway Migration V6 (PostgreSQL Compatible)
-- ============================================================

ALTER TABLE bills ADD COLUMN IF NOT EXISTS printed_by BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_bill_printed_by'
    ) THEN
        ALTER TABLE bills ADD CONSTRAINT fk_bill_printed_by FOREIGN KEY (printed_by) REFERENCES users(id);
    END IF;
END $$;
