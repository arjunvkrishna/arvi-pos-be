-- ============================================================
-- Migration V23: Add subheader2 (address) and KOT custom templates
-- ============================================================

ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS subheader2 VARCHAR(500) DEFAULT 'Infopark Expy, Rajagiri Valley, Chittethukara, Kakkanad, Kochi, Keralam 682037';
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS kot_header_title VARCHAR(150) DEFAULT '*** KITCHEN ORDER ***';
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS kot_subheader VARCHAR(500) DEFAULT '';
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS kot_footer_message VARCHAR(150) DEFAULT '--- END OF KOT ---';

-- Ensure subheader (quote) and subheader2 (address) are cleanly split
UPDATE printer_settings
SET subheader = 'Where time tastes better',
    subheader2 = 'Infopark Expy, Rajagiri Valley, Chittethukara, Kakkanad, Kochi, Keralam 682037'
WHERE subheader LIKE '%Infopark%' OR subheader IS NULL OR subheader = '';
