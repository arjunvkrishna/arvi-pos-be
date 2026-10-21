-- ============================================================
-- NERAM POS System - Volume & Unit Measurements for Inventory & Supplier Items
-- Flyway Migration V38 (PostgreSQL Compatible)
-- ============================================================

-- 1. Add volume fields to inventory_items
ALTER TABLE inventory_items 
    ADD COLUMN IF NOT EXISTS unit_volume DOUBLE PRECISION DEFAULT 1.0,
    ADD COLUMN IF NOT EXISTS volume_unit VARCHAR(50),
    ADD COLUMN IF NOT EXISTS total_volume DOUBLE PRECISION DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS cost_per_volume NUMERIC(12,2) DEFAULT 0.0;

-- Populate existing total_volume and unit_volume for existing inventory rows
UPDATE inventory_items 
SET unit_volume = COALESCE(unit_volume, 1.0),
    volume_unit = COALESCE(volume_unit, unit),
    total_volume = COALESCE(total_volume, quantity * COALESCE(unit_volume, 1.0)),
    cost_per_volume = COALESCE(cost_per_volume, cost_per_unit)
WHERE unit_volume IS NULL OR total_volume IS NULL;

-- 2. Add volume fields to supplier_invoice_items
ALTER TABLE supplier_invoice_items 
    ADD COLUMN IF NOT EXISTS unit_volume DOUBLE PRECISION DEFAULT 1.0,
    ADD COLUMN IF NOT EXISTS volume_unit VARCHAR(50),
    ADD COLUMN IF NOT EXISTS total_volume DOUBLE PRECISION DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS cost_per_volume NUMERIC(12,2) DEFAULT 0.0;

-- Populate existing supplier_invoice_items
UPDATE supplier_invoice_items
SET unit_volume = COALESCE(unit_volume, 1.0),
    volume_unit = COALESCE(volume_unit, unit),
    total_volume = COALESCE(total_volume, quantity * COALESCE(unit_volume, 1.0)),
    cost_per_volume = COALESCE(cost_per_volume, unit_price)
WHERE unit_volume IS NULL OR total_volume IS NULL;

-- 3. Add volume fields to inventory_transactions
ALTER TABLE inventory_transactions 
    ADD COLUMN IF NOT EXISTS unit_volume DOUBLE PRECISION DEFAULT 1.0,
    ADD COLUMN IF NOT EXISTS volume_unit VARCHAR(50),
    ADD COLUMN IF NOT EXISTS total_volume DOUBLE PRECISION DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS cost_per_volume NUMERIC(12,2) DEFAULT 0.0;
