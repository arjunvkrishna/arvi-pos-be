-- ============================================================
-- NERAM POS System - Kitchen Order Ticket (KOT) & Table Orders
-- Flyway Migration V3 (PostgreSQL Compatible)
-- ============================================================

ALTER TABLE bills ADD COLUMN IF NOT EXISTS kot_printed_at TIMESTAMP NULL;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS kot_number VARCHAR(100) NULL;
