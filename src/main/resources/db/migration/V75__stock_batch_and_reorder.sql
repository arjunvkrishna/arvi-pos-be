-- ============================================================
-- NERAM POS - Stock Batch Numbers, Expiry Index & Reorder Level (V75)
-- ============================================================

ALTER TABLE inventory_items
    ADD COLUMN IF NOT EXISTS reorder_level NUMERIC(10,3) NOT NULL DEFAULT 0;

ALTER TABLE supplier_invoice_items
    ADD COLUMN IF NOT EXISTS batch_number VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_supp_inv_items_expiry ON supplier_invoice_items(inventory_item_id, expiry_date);
