-- ============================================================
-- NERAM POS System - Takeaway Tokens & Line-Item Parcel Support
-- Flyway Migration V48 (PostgreSQL Compatible)
-- ============================================================

ALTER TABLE bills ADD COLUMN IF NOT EXISTS takeaway_token VARCHAR(50);
CREATE INDEX IF NOT EXISTS idx_bills_takeaway_token ON bills(takeaway_token);

ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS is_takeaway BOOLEAN NOT NULL DEFAULT FALSE;
