-- ============================================================
-- V86: branch (outlet) ownership for the modules ported from NERAM POS (V73-V85)
--   shift_schedules / tip_pool_employee_shares  -> scoped through the employee's branch (no column needed)
--   tip_pool_distributions, documents, report_export_jobs -> own outlet_id (NULL = whole cafe)
-- ============================================================

ALTER TABLE tip_pool_distributions ADD COLUMN IF NOT EXISTS outlet_id BIGINT;
ALTER TABLE documents              ADD COLUMN IF NOT EXISTS outlet_id BIGINT;
ALTER TABLE report_export_jobs     ADD COLUMN IF NOT EXISTS outlet_id BIGINT;

-- One tip pool per business day and branch (it used to be one per day for the whole cafe).
ALTER TABLE tip_pool_distributions DROP CONSTRAINT IF EXISTS tip_pool_distributions_distribution_date_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_tip_pool_date_outlet ON tip_pool_distributions (distribution_date, COALESCE(outlet_id, 0));

CREATE INDEX IF NOT EXISTS idx_documents_outlet          ON documents (outlet_id);
CREATE INDEX IF NOT EXISTS idx_report_export_jobs_outlet ON report_export_jobs (outlet_id);
-- Lets the worker find the next pending job, and the nightly purge find old ones, without scanning the table.
CREATE INDEX IF NOT EXISTS idx_report_export_jobs_status_created ON report_export_jobs (status, created_at);
