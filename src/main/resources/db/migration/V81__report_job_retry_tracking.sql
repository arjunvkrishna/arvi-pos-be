-- ============================================================
-- NERAM POS - Report export job claim/retry tracking (V81)
-- started_at marks when a worker claimed the job; attempts counts claims so
-- stuck-job recovery can retry a few times before marking the job FAILED.
-- ============================================================
ALTER TABLE report_export_jobs
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS attempts INT NOT NULL DEFAULT 0;
