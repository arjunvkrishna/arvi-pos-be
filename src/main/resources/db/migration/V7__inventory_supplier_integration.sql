-- ============================================================
-- NERAM POS System - Inventory & Supplier Purchase Integration
-- Flyway Migration V7 (PostgreSQL Compatible)
-- ============================================================

-- 1. Supplier Invoice Line Items
CREATE TABLE IF NOT EXISTS supplier_invoice_items (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    inventory_item_id BIGINT,
    product_name VARCHAR(255) NOT NULL,
    quantity DOUBLE PRECISION NOT NULL,
    unit_price DOUBLE PRECISION NOT NULL,
    total_price DOUBLE PRECISION NOT NULL,
    unit VARCHAR(50) DEFAULT 'pcs',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inv_item_invoice FOREIGN KEY (invoice_id) REFERENCES supplier_invoices(id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_item_inventory FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_inv_item_invoice ON supplier_invoice_items(invoice_id);
CREATE INDEX IF NOT EXISTS idx_inv_item_inventory ON supplier_invoice_items(inventory_item_id);

-- 2. Inventory Transaction History / Log
CREATE TABLE IF NOT EXISTS inventory_transactions (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL,
    transaction_type VARCHAR(50) NOT NULL, -- STOCK_IN, STOCK_OUT, ADJUSTMENT
    quantity DOUBLE PRECISION NOT NULL,
    previous_stock DOUBLE PRECISION NOT NULL,
    new_stock DOUBLE PRECISION NOT NULL,
    user_id BIGINT,
    user_name VARCHAR(100),
    user_role VARCHAR(50),
    supplier_id BIGINT,
    supplier_name VARCHAR(255),
    invoice_id BIGINT,
    invoice_reference VARCHAR(150),
    unit_price DOUBLE PRECISION,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_txn_item FOREIGN KEY (item_id) REFERENCES inventory_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_txn_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_txn_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE SET NULL,
    CONSTRAINT fk_txn_invoice FOREIGN KEY (invoice_id) REFERENCES supplier_invoices(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_txn_item ON inventory_transactions(item_id);
CREATE INDEX IF NOT EXISTS idx_txn_type ON inventory_transactions(transaction_type);
CREATE INDEX IF NOT EXISTS idx_txn_supplier ON inventory_transactions(supplier_id);
CREATE INDEX IF NOT EXISTS idx_txn_user ON inventory_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_txn_created_at ON inventory_transactions(created_at);
