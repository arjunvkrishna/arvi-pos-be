-- ============================================================
-- NERAM POS - V50: Add Juice / Beverage KOT Category Routing
-- Adds juice_kot_category_ids column to printer_settings
-- storing comma-separated category IDs routed to the Juice KOT printer.
-- ============================================================

ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS juice_kot_category_ids VARCHAR(1000) NOT NULL DEFAULT '';

ALTER TABLE "printer_settings" ADD COLUMN IF NOT EXISTS "juice_kot_category_ids" VARCHAR(1000) NOT NULL DEFAULT '';
