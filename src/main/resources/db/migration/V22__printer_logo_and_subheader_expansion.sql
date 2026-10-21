-- ============================================================
-- Migration V22: Support logo toggle and expanded subheader/footer in printer settings
-- ============================================================

ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS logo_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE printer_settings ALTER COLUMN subheader TYPE VARCHAR(500);
ALTER TABLE printer_settings ALTER COLUMN footer_message TYPE VARCHAR(500);

-- Update default subheader to store address if it was the initial placeholder
UPDATE printer_settings
SET subheader = 'Infopark Expy, Rajagiri Valley, Chittethukara, Kakkanad, Kochi, Keralam 682037'
WHERE subheader = 'Where time tastes better' OR subheader IS NULL;
