-- ============================================================
-- NERAM POS - Bill soft delete (V70, was V65 on develop - renumbered because this branch already has a V65)
-- An admin/manager can remove one of the last 5 closed bills from the Bill Log with a mandatory reason.
-- The row is kept (deleted_at is set) so it stays traceable; the app hides it everywhere. Additive only.
-- ============================================================
ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_by BIGINT,
    ADD COLUMN IF NOT EXISTS deleted_by_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS delete_reason TEXT;
CREATE INDEX IF NOT EXISTS idx_bills_not_deleted_created ON bills(created_at DESC) WHERE deleted_at IS NULL;
