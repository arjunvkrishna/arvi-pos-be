-- ============================================================
-- NERAM POS - Pack conversion for inventory (V61)
-- Stock is counted in ONE stock unit (inventory_items.unit: bottle, packet, kg...).
-- An item may optionally come in a pack (e.g. 1 case = 12 bottles).
-- Additive only: existing rows keep their meaning (no pack => 1:1).
-- ============================================================
ALTER TABLE inventory_items
    ADD COLUMN IF NOT EXISTS pack_name VARCHAR(50),
    ADD COLUMN IF NOT EXISTS pack_size DOUBLE PRECISION;

-- How a purchase line was entered, and the stock it added, in stock units.
-- NULL stock_quantity (all existing rows) means "same as quantity".
ALTER TABLE supplier_invoice_items
    ADD COLUMN IF NOT EXISTS purchase_mode VARCHAR(10),
    ADD COLUMN IF NOT EXISTS pack_name VARCHAR(50),
    ADD COLUMN IF NOT EXISTS pack_size DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS stock_quantity DOUBLE PRECISION;
