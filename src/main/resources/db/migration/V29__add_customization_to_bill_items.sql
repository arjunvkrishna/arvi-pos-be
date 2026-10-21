-- ============================================================
-- NERAM POS System - Dish Customization / Kitchen Notes
-- Flyway Migration V14 (PostgreSQL Compatible)
-- ============================================================

ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS customization VARCHAR(500) NULL;
