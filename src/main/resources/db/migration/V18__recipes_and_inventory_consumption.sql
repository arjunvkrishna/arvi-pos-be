-- ============================================================
-- NERAM POS System - Recipes and Ingredient Stock Usage
-- Flyway Migration V18 (PostgreSQL Compatible)
-- ============================================================

-- 1. Recipe items mapping Menu Items to Inventory Ingredients
CREATE TABLE IF NOT EXISTS recipes (
    id BIGSERIAL PRIMARY KEY,
    menu_item_id BIGINT NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    inventory_item_id BIGINT NOT NULL REFERENCES inventory_items(id) ON DELETE RESTRICT,
    quantity_per_serving NUMERIC(10,3) NOT NULL,
    unit VARCHAR(50) NOT NULL DEFAULT 'g',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_recipe_menu_inventory UNIQUE (menu_item_id, inventory_item_id)
);

CREATE INDEX IF NOT EXISTS idx_recipe_menu_item ON recipes(menu_item_id);
CREATE INDEX IF NOT EXISTS idx_recipe_inventory_item ON recipes(inventory_item_id);

-- 2. Enhance Inventory Transactions with order/recipe tracking
ALTER TABLE inventory_transactions 
    ADD COLUMN IF NOT EXISTS bill_id BIGINT REFERENCES bills(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS bill_item_id BIGINT REFERENCES bill_items(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS recipe_id BIGINT REFERENCES recipes(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS unit VARCHAR(50) DEFAULT 'pcs';

-- Prevent duplicate SALE_CONSUMPTION for the same bill item
CREATE UNIQUE INDEX IF NOT EXISTS uq_sale_consumption_per_item 
    ON inventory_transactions (bill_item_id, transaction_type) 
    WHERE transaction_type = 'SALE_CONSUMPTION' AND bill_item_id IS NOT NULL;
