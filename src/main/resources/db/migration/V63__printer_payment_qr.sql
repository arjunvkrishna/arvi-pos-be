-- ============================================================
-- Migration V63: switch for the payment QR printed on customer bills (on by default)
-- ============================================================

ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS payment_qr_enabled BOOLEAN NOT NULL DEFAULT TRUE;
