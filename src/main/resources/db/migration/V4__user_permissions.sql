-- ============================================================
-- NERAM POS System - Granular User Permissions
-- Flyway Migration V4 (PostgreSQL Compatible)
-- ============================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS can_take_orders BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS can_manage_inventory BOOLEAN NOT NULL DEFAULT FALSE;
