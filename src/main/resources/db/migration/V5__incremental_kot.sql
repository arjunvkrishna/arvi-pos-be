-- ============================================================
-- NERAM POS System - Incremental Kitchen Order Ticket (KOT)
-- Flyway Migration V5 (PostgreSQL Compatible)
-- ============================================================

ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS printed_quantity INT NOT NULL DEFAULT 0;
