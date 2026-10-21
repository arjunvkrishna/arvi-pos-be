-- ============================================================
-- NERAM POS System - Order Type & Outdoor Seating Tokens
-- Flyway Migration V12 (PostgreSQL Compatible)
-- ============================================================

ALTER TABLE bills ADD COLUMN IF NOT EXISTS order_type VARCHAR(20) NOT NULL DEFAULT 'TABLE';
ALTER TABLE bills ADD COLUMN IF NOT EXISTS outdoor_token VARCHAR(20);
