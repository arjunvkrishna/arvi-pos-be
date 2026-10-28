-- ============================================================
-- V87: things that were unique for the whole cafe but must be unique per branch, and floors for tables
--   * a table name (T1, T2 ...) only has to be unique inside its branch
--   * every branch has its own cash drawer: one open shift per branch (and per register inside a branch)
--   * tables can be grouped by floor / section
-- ============================================================

ALTER TABLE dining_tables DROP CONSTRAINT IF EXISTS dining_tables_name_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_dining_tables_branch_name ON dining_tables (COALESCE(outlet_id, 0), lower(name));
ALTER TABLE dining_tables ADD COLUMN IF NOT EXISTS floor VARCHAR(50);
CREATE INDEX IF NOT EXISTS idx_dining_tables_outlet_floor ON dining_tables (outlet_id, floor, display_order);

-- Cash drawers: this used to allow ONE open shift in the whole cafe, so a second branch could not open its drawer.
DROP INDEX IF EXISTS uq_single_open_cashier_shift;
DROP INDEX IF EXISTS uq_one_open_shift_per_register;
CREATE UNIQUE INDEX IF NOT EXISTS uq_single_open_cashier_shift_per_branch
    ON cashier_shifts (COALESCE(outlet_id, 0)) WHERE status = 'OPEN';
CREATE UNIQUE INDEX IF NOT EXISTS uq_one_open_shift_per_register_per_branch
    ON cashier_shifts (COALESCE(outlet_id, 0), register_id) WHERE status = 'OPEN';
