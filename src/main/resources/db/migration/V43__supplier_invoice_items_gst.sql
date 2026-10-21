-- V43: Add item-level GST fields to supplier_invoice_items
ALTER TABLE supplier_invoice_items ADD COLUMN IF NOT EXISTS gst_rate DOUBLE PRECISION DEFAULT 0.0;
ALTER TABLE supplier_invoice_items ADD COLUMN IF NOT EXISTS gst_amount NUMERIC(12,2) DEFAULT 0.0;

-- Backfill existing items from invoice gst_rate if present
UPDATE supplier_invoice_items sii
SET gst_rate = COALESCE(si.gst_rate, 0.0),
    gst_amount = ROUND((sii.total_price * COALESCE(si.gst_rate, 0.0) / 100.0)::numeric, 2)
FROM supplier_invoices si
WHERE sii.invoice_id = si.id AND (sii.gst_rate IS NULL OR sii.gst_rate = 0.0) AND si.gst_rate > 0;
