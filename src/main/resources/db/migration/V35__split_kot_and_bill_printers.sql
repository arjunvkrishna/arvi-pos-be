-- ============================================================
-- Migration V34: Split KOT and Bill Thermal Printers & Telemetry
-- Support dedicated POS printers for KOT and Bill (default 192.168.1.37:9100)
-- ============================================================

ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS bill_host VARCHAR(100) NOT NULL DEFAULT '192.168.1.37';
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS bill_port INT NOT NULL DEFAULT 9100;
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS bill_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS kot_host VARCHAR(100) NOT NULL DEFAULT '192.168.1.47';
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS kot_port INT NOT NULL DEFAULT 9100;
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS kot_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Synchronize existing settings with initial defaults
UPDATE printer_settings
SET bill_host = COALESCE(NULLIF(host, ''), '192.168.1.37'),
    bill_port = COALESCE(port, 9100),
    bill_enabled = COALESCE(enabled, TRUE),
    kot_host = '192.168.1.47',
    kot_port = 9100,
    kot_enabled = TRUE
WHERE id = 1;
