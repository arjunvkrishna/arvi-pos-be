-- ============================================================
-- NERAM POS - Item Modifiers, Add-ons & Combo Meals (V73)
-- ============================================================

CREATE TABLE modifier_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    min_selections INT NOT NULL DEFAULT 0,
    max_selections INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE modifiers (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES modifier_groups(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    price_adjustment NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    inventory_item_id BIGINT REFERENCES inventory_items(id) ON DELETE SET NULL,
    inventory_quantity NUMERIC(10,3) DEFAULT 0
);

CREATE TABLE menu_item_modifier_groups (
    menu_item_id BIGINT NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    modifier_group_id BIGINT NOT NULL REFERENCES modifier_groups(id) ON DELETE CASCADE,
    PRIMARY KEY (menu_item_id, modifier_group_id)
);

CREATE TABLE combo_groups (
    id BIGSERIAL PRIMARY KEY,
    combo_menu_item_id BIGINT NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    min_choices INT NOT NULL DEFAULT 1,
    max_choices INT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE combo_group_items (
    id BIGSERIAL PRIMARY KEY,
    combo_group_id BIGINT NOT NULL REFERENCES combo_groups(id) ON DELETE CASCADE,
    menu_item_id BIGINT NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    additional_price NUMERIC(10,2) NOT NULL DEFAULT 0.00
);

ALTER TABLE bill_items
    ADD COLUMN IF NOT EXISTS modifiers_snapshot JSONB,
    ADD COLUMN IF NOT EXISTS combo_snapshot JSONB;
