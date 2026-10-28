-- ============================================================
-- NERAM POS - Direct printing from the staff Android app (V82)
-- direct_print_ssids: comma-separated Wi-Fi names (e.g. the cafe Wi-Fi). A phone connected to one of
-- them prints straight to the LAN printers; anywhere else printing goes through the cloud bridge.
-- Empty = direct printing off.
-- ============================================================
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS direct_print_ssids VARCHAR(255) NOT NULL DEFAULT '';
