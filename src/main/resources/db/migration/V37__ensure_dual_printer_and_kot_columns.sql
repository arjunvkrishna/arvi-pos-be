-- ============================================================
-- Migration V36: Ensure Dual Printer Settings and KOT Columns
-- Idempotently ensures bill and kot printer columns exist in printer_settings
-- and KOT tracking columns exist in bills table.
-- ============================================================

-- Ensure printer_settings has bill and kot columns
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS bill_host VARCHAR(100) NOT NULL DEFAULT '192.168.1.37';
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS bill_port INT NOT NULL DEFAULT 9100;
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS bill_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS kot_host VARCHAR(100) NOT NULL DEFAULT '192.168.1.47';
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS kot_port INT NOT NULL DEFAULT 9100;
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS kot_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Ensure bills table has KOT tracking columns
ALTER TABLE bills ADD COLUMN IF NOT EXISTS kot_update_count INT NOT NULL DEFAULT 0;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS kot_updated_at TIMESTAMP;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS kot_last_changes TEXT;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS kot_cancelled_at TIMESTAMP;

-- Synchronize printer_settings row 1 with defaults if existing values are empty
UPDATE printer_settings
SET bill_host = COALESCE(NULLIF(bill_host, ''), COALESCE(NULLIF(host, ''), '192.168.1.37')),
    bill_port = COALESCE(bill_port, COALESCE(port, 9100)),
    bill_enabled = COALESCE(bill_enabled, COALESCE(enabled, TRUE)),
    kot_host = COALESCE(NULLIF(kot_host, ''), '192.168.1.47'),
    kot_port = COALESCE(kot_port, 9100),
    kot_enabled = COALESCE(kot_enabled, TRUE)
WHERE id = 1;
