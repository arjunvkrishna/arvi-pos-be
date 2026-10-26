-- ============================================================
-- NERAM POS - Stock: expiry dates, automatic-deduction switch, waste reason types (V65)
-- Additive only. Existing rows keep today's behaviour: every item still deducts automatically when dishes sell,
-- no expiry is tracked until a purchase line carries an expiry date (or the item has a shelf life), reason type stays empty.
-- ============================================================
ALTER TABLE inventory_items
    ADD COLUMN IF NOT EXISTS auto_deduct      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS shelf_life_days  INTEGER,
    ADD COLUMN IF NOT EXISTS expiry_warn_days INTEGER NOT NULL DEFAULT 2;

ALTER TABLE supplier_invoice_items
    ADD COLUMN IF NOT EXISTS expiry_date DATE;

ALTER TABLE inventory_transactions
    ADD COLUMN IF NOT EXISTS reason_type VARCHAR(20);
