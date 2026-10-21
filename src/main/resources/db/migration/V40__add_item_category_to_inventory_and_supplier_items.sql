-- V40: Add item_category to inventory_items, supplier_invoice_items, and inventory_transactions
ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS item_category VARCHAR(50) NOT NULL DEFAULT 'Stock';
ALTER TABLE supplier_invoice_items ADD COLUMN IF NOT EXISTS item_category VARCHAR(50) NOT NULL DEFAULT 'Stock';
ALTER TABLE inventory_transactions ADD COLUMN IF NOT EXISTS item_category VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_inventory_items_category ON inventory_items(item_category);
CREATE INDEX IF NOT EXISTS idx_supplier_invoice_items_category ON supplier_invoice_items(item_category);
