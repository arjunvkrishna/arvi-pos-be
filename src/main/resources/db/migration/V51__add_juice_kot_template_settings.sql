-- ============================================================
-- NERAM POS - V49: Add Juice KOT Template Settings
-- Adds dedicated header_title, subheader, and footer_message
-- columns for the Juice KOT thermal printer layout.
-- ============================================================

ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS juice_kot_header_title VARCHAR(150) NOT NULL DEFAULT '*** JUICE ORDER ***';
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS juice_kot_subheader VARCHAR(500) NOT NULL DEFAULT 'NERAM - JUICE BAR';
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS juice_kot_footer_message VARCHAR(150) NOT NULL DEFAULT '--- END OF JUICE KOT ---';

ALTER TABLE "printer_settings" ADD COLUMN IF NOT EXISTS "juice_kot_header_title" VARCHAR(150) NOT NULL DEFAULT '*** JUICE ORDER ***';
ALTER TABLE "printer_settings" ADD COLUMN IF NOT EXISTS "juice_kot_subheader" VARCHAR(500) NOT NULL DEFAULT 'NERAM - JUICE BAR';
ALTER TABLE "printer_settings" ADD COLUMN IF NOT EXISTS "juice_kot_footer_message" VARCHAR(150) NOT NULL DEFAULT '--- END OF JUICE KOT ---';
