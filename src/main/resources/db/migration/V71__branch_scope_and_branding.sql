-- ============================================================
-- V71: branch (outlet) ownership of operational data, cafe/branch branding, management toggles
-- ============================================================

-- 1. Tables that did not yet record which branch a row belongs to. Existing rows go to the cafe's first branch.
ALTER TABLE print_jobs           ADD COLUMN IF NOT EXISTS outlet_id BIGINT;
ALTER TABLE system_notifications ADD COLUMN IF NOT EXISTS outlet_id BIGINT;
ALTER TABLE stock_counts         ADD COLUMN IF NOT EXISTS outlet_id BIGINT;
ALTER TABLE expense_templates    ADD COLUMN IF NOT EXISTS outlet_id BIGINT;
ALTER TABLE supplier_invoices    ADD COLUMN IF NOT EXISTS outlet_id BIGINT;

UPDATE print_jobs           SET outlet_id = (SELECT MIN(id) FROM outlets) WHERE outlet_id IS NULL;
UPDATE system_notifications SET outlet_id = (SELECT MIN(id) FROM outlets) WHERE outlet_id IS NULL;
UPDATE stock_counts         SET outlet_id = (SELECT MIN(id) FROM outlets) WHERE outlet_id IS NULL;
UPDATE expense_templates    SET outlet_id = (SELECT MIN(id) FROM outlets) WHERE outlet_id IS NULL;
UPDATE supplier_invoices    SET outlet_id = (SELECT MIN(id) FROM outlets) WHERE outlet_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_print_jobs_outlet           ON print_jobs(outlet_id, status);
CREATE INDEX IF NOT EXISTS idx_system_notifications_outlet ON system_notifications(outlet_id);
CREATE INDEX IF NOT EXISTS idx_stock_counts_outlet         ON stock_counts(outlet_id);
CREATE INDEX IF NOT EXISTS idx_supplier_invoices_outlet    ON supplier_invoices(outlet_id);
CREATE INDEX IF NOT EXISTS idx_employees_outlet            ON employees(outlet_id);
CREATE INDEX IF NOT EXISTS idx_users_outlet                ON users(outlet_id);

-- Rows created before branches were enforced may carry the old default outlet id even if that outlet is gone.
UPDATE bills            SET outlet_id = (SELECT MIN(id) FROM outlets) WHERE outlet_id IS NULL;
UPDATE dining_tables    SET outlet_id = (SELECT MIN(id) FROM outlets) WHERE outlet_id IS NULL;
UPDATE cashier_shifts   SET outlet_id = (SELECT MIN(id) FROM outlets) WHERE outlet_id IS NULL;
UPDATE cafe_expenses    SET outlet_id = (SELECT MIN(id) FROM outlets) WHERE outlet_id IS NULL;
UPDATE inventory_items  SET outlet_id = (SELECT MIN(id) FROM outlets) WHERE outlet_id IS NULL;
UPDATE employees        SET outlet_id = (SELECT MIN(id) FROM outlets) WHERE outlet_id IS NULL;
UPDATE printer_settings SET outlet_id = (SELECT MIN(id) FROM outlets) WHERE outlet_id IS NULL;

-- 2. Per-user toggles for the areas managers can delegate.
ALTER TABLE users ADD COLUMN IF NOT EXISTS can_manage_menu     BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS can_manage_settings BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Branding: a tagline/quote for the cafe, and uploaded images (logo, payment QR) that belong to the cafe or,
--    as an override, to one branch.
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS tagline VARCHAR(200);

CREATE TABLE IF NOT EXISTS branding_assets (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT REFERENCES tenants(id) ON DELETE CASCADE,
    kind         VARCHAR(20)  NOT NULL,
    outlet_id    BIGINT REFERENCES outlets(id) ON DELETE CASCADE,
    content_type VARCHAR(50)  NOT NULL,
    data         BYTEA        NOT NULL,
    updated_by   VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT branding_assets_kind_chk CHECK (kind IN ('LOGO', 'PAYMENT_QR'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_branding_assets_kind_outlet ON branding_assets(kind, COALESCE(outlet_id, 0));

-- 4. One printer-settings row per branch: the old singleton row (id 1) becomes the first branch's row.
CREATE INDEX IF NOT EXISTS idx_printer_settings_outlet ON printer_settings(outlet_id);
