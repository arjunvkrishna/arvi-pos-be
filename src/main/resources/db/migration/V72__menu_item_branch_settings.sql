-- ============================================================
-- V72: per-branch menu overrides. The menu itself stays shared; a branch may sell an item at its own price and mark
-- it sold out (or available) for itself only. A NULL column means "use the cafe-wide value".
-- ============================================================
CREATE TABLE IF NOT EXISTS menu_item_branch_settings (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT REFERENCES tenants(id) ON DELETE CASCADE,
    menu_item_id BIGINT        NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    outlet_id    BIGINT        NOT NULL REFERENCES outlets(id) ON DELETE CASCADE,
    price        NUMERIC(12,2),
    is_available BOOLEAN,
    updated_by   VARCHAR(100),
    updated_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_menu_item_branch UNIQUE (menu_item_id, outlet_id),
    CONSTRAINT chk_menu_item_branch_price CHECK (price IS NULL OR price >= 0)
);
CREATE INDEX IF NOT EXISTS idx_menu_item_branch_outlet ON menu_item_branch_settings(outlet_id);
