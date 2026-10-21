-- ============================================================
-- NERAM POS - V48: Add Juice KOT Printer Configuration
-- Adds dedicated host, port, and enabled columns for the third
-- thermal printer configuration named Juice KOT.
-- ============================================================

ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS juice_kot_host VARCHAR(100) NOT NULL DEFAULT '192.168.1.42';
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS juice_kot_port INT NOT NULL DEFAULT 9100;
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS juice_kot_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE "printer_settings" ADD COLUMN IF NOT EXISTS "juice_kot_host" VARCHAR(100) NOT NULL DEFAULT '192.168.1.42';
ALTER TABLE "printer_settings" ADD COLUMN IF NOT EXISTS "juice_kot_port" INT NOT NULL DEFAULT 9100;
ALTER TABLE "printer_settings" ADD COLUMN IF NOT EXISTS "juice_kot_enabled" BOOLEAN NOT NULL DEFAULT TRUE;
