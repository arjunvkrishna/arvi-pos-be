-- ============================================================
-- NERAM POS System - Shift Module Settings
-- Flyway Migration V19 (PostgreSQL Compatible)
-- ============================================================

CREATE TABLE IF NOT EXISTS shift_settings (
    id BIGINT PRIMARY KEY DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_by_name VARCHAR(100)
);

INSERT INTO shift_settings (id, enabled) VALUES (1, true)
ON CONFLICT (id) DO NOTHING;
