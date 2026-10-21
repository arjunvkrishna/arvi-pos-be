-- ============================================================
-- NERAM POS System - Fast Audit Log Query Index
-- Flyway Migration V15 (PostgreSQL Compatible)
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_audit_logs_perf_user_date ON audit_logs (performed_by_name, performed_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_perf_id_date ON audit_logs (performed_by, performed_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action_date ON audit_logs (action, performed_at DESC);
