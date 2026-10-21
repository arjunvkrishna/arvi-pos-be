-- V39: Add logo spacing, line pitch, and font readability options to printer_settings
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS logo_size INT NOT NULL DEFAULT 130;
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS logo_gap INT NOT NULL DEFAULT 2;
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS line_spacing INT NOT NULL DEFAULT 24;
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS header_spacing INT NOT NULL DEFAULT 1;
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS footer_spacing INT NOT NULL DEFAULT 1;
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS font_size VARCHAR(50) NOT NULL DEFAULT 'DOUBLE_HEIGHT';
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS print_style VARCHAR(50) NOT NULL DEFAULT 'CRISP_DARK';
