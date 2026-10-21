-- ============================================================
-- NERAM POS System - GST Settings & Bill Enhancement
-- Flyway Migration V14 (PostgreSQL Compatible)
-- ============================================================

CREATE TABLE IF NOT EXISTS gst_settings (
    id BIGINT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    gstin VARCHAR(50) NOT NULL DEFAULT '32CQRPS5214H3ZK',
    cgst_percent NUMERIC(5,2) NOT NULL DEFAULT 2.50,
    sgst_percent NUMERIC(5,2) NOT NULL DEFAULT 2.50,
    round_off_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL
);

-- Seed default GST setting row (id = 1)
INSERT INTO gst_settings (id, enabled, gstin, cgst_percent, sgst_percent, round_off_enabled, updated_at)
VALUES (1, TRUE, '32CQRPS5214H3ZK', 2.50, 2.50, FALSE, NOW())
ON CONFLICT (id) DO NOTHING;

-- Add GST fields to bills table
ALTER TABLE bills ADD COLUMN IF NOT EXISTS gst_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS gstin VARCHAR(50);
ALTER TABLE bills ADD COLUMN IF NOT EXISTS cgst_percent NUMERIC(5,2) DEFAULT 0.0;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS sgst_percent NUMERIC(5,2) DEFAULT 0.0;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS cgst_amount NUMERIC(10,2) DEFAULT 0.0;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS sgst_amount NUMERIC(10,2) DEFAULT 0.0;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS round_off NUMERIC(10,2) DEFAULT 0.0;
