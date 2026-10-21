-- ============================================================
-- Migration V35: KOT Update and Cancellation Tracking
-- Track KOT reprint versions, update times, cancellation notices
-- ============================================================

ALTER TABLE bills ADD COLUMN IF NOT EXISTS kot_update_count INT NOT NULL DEFAULT 0;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS kot_updated_at TIMESTAMP;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS kot_last_changes TEXT;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS kot_cancelled_at TIMESTAMP;
