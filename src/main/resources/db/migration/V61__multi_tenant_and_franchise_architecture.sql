-- V61__multi_tenant_and_franchise_architecture.sql
-- Multi-Tenant SaaS and Multi-Franchise / Multi-Outlet Management Architecture
-- Supports:
-- 1. SaaS Platform Super Admin (Tenants onboarding, subscription management, platform metrics)
-- 2. Cafe Admin (Multi-Franchise / Multi-Outlet management for brand, central catalog, consolidated reports)
-- 3. Branch / Outlet Managers, Cashiers, Kitchen staff scoped to specific outlets.

-- 1. Tenants (Cafes / Restaurant Brands)
CREATE TABLE IF NOT EXISTS tenants (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(100) NOT NULL UNIQUE,
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50),
    address TEXT,
    subscription_plan VARCHAR(50) NOT NULL DEFAULT 'PRO',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    max_outlets INT NOT NULL DEFAULT 5,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    currency_symbol VARCHAR(10) NOT NULL DEFAULT '₹',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Outlets (Franchises / Branches per Tenant)
CREATE TABLE IF NOT EXISTS outlets (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL,
    address TEXT,
    phone VARCHAR(50),
    gst_number VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_outlet_code UNIQUE (tenant_id, code)
);

-- 3. Seed initial default Tenant and default Outlet (backward compatibility)
INSERT INTO tenants (id, name, code, contact_email, contact_phone, address, subscription_plan, status, max_outlets, currency, currency_symbol)
VALUES (1, 'Main Cafe', 'main-cafe', 'contact@cafe.internal', '+91 9876543210', 'Main Street, Central Branch', 'ENTERPRISE', 'ACTIVE', 10, 'INR', '₹')
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('tenants', 'id'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM tenants), 1));

INSERT INTO outlets (id, tenant_id, name, code, address, phone, is_active)
VALUES (1, 1, 'Main Branch', 'MAIN-01', 'Ground Floor, Cafe Central', '+91 9876543210', TRUE)
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('outlets', 'id'), GREATEST((SELECT COALESCE(MAX(id), 1) FROM outlets), 1));

-- 4. Alter existing core tables to add tenant_id and outlet_id with safe defaults

-- users (tenant_id nullable for SUPER_ADMIN, set for cafe-level users)
ALTER TABLE users ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id) ON DELETE SET NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS outlet_id BIGINT REFERENCES outlets(id) ON DELETE SET NULL;

-- employees
ALTER TABLE employees ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id) ON DELETE CASCADE DEFAULT 1;
ALTER TABLE employees ADD COLUMN IF NOT EXISTS outlet_id BIGINT REFERENCES outlets(id) ON DELETE SET NULL DEFAULT 1;

-- categories
ALTER TABLE categories ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id) ON DELETE CASCADE DEFAULT 1;

-- menu_items
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id) ON DELETE CASCADE DEFAULT 1;
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS outlet_id BIGINT REFERENCES outlets(id) ON DELETE SET NULL;

-- bills
ALTER TABLE bills ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id) ON DELETE CASCADE DEFAULT 1;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS outlet_id BIGINT REFERENCES outlets(id) ON DELETE SET NULL DEFAULT 1;

-- dining_tables
ALTER TABLE dining_tables ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id) ON DELETE CASCADE DEFAULT 1;
ALTER TABLE dining_tables ADD COLUMN IF NOT EXISTS outlet_id BIGINT REFERENCES outlets(id) ON DELETE SET NULL DEFAULT 1;

-- cashier_shifts
ALTER TABLE cashier_shifts ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id) ON DELETE CASCADE DEFAULT 1;
ALTER TABLE cashier_shifts ADD COLUMN IF NOT EXISTS outlet_id BIGINT REFERENCES outlets(id) ON DELETE SET NULL DEFAULT 1;

-- cafe_expenses
ALTER TABLE cafe_expenses ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id) ON DELETE CASCADE DEFAULT 1;
ALTER TABLE cafe_expenses ADD COLUMN IF NOT EXISTS outlet_id BIGINT REFERENCES outlets(id) ON DELETE SET NULL DEFAULT 1;

-- inventory_items
ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id) ON DELETE CASCADE DEFAULT 1;
ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS outlet_id BIGINT REFERENCES outlets(id) ON DELETE SET NULL DEFAULT 1;

-- suppliers
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id) ON DELETE CASCADE DEFAULT 1;

-- promotions
ALTER TABLE promotions ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id) ON DELETE CASCADE DEFAULT 1;
ALTER TABLE promotions ADD COLUMN IF NOT EXISTS outlet_id BIGINT REFERENCES outlets(id) ON DELETE SET NULL;

-- recipes
ALTER TABLE recipes ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id) ON DELETE CASCADE DEFAULT 1;

-- printer_settings
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id) ON DELETE CASCADE DEFAULT 1;
ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS outlet_id BIGINT REFERENCES outlets(id) ON DELETE SET NULL DEFAULT 1;

-- 5. Backfill existing records to tenant_id=1 and outlet_id=1
UPDATE users SET tenant_id = 1 WHERE tenant_id IS NULL AND role != 'SUPER_ADMIN';
UPDATE users SET outlet_id = 1 WHERE outlet_id IS NULL AND role NOT IN ('SUPER_ADMIN', 'ADMIN');
UPDATE employees SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE employees SET outlet_id = 1 WHERE outlet_id IS NULL;
UPDATE categories SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE menu_items SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE bills SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE bills SET outlet_id = 1 WHERE outlet_id IS NULL;
UPDATE dining_tables SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE dining_tables SET outlet_id = 1 WHERE outlet_id IS NULL;
UPDATE cashier_shifts SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE cashier_shifts SET outlet_id = 1 WHERE outlet_id IS NULL;
UPDATE cafe_expenses SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE cafe_expenses SET outlet_id = 1 WHERE outlet_id IS NULL;
UPDATE inventory_items SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE inventory_items SET outlet_id = 1 WHERE outlet_id IS NULL;
UPDATE suppliers SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE promotions SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE recipes SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE printer_settings SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE printer_settings SET outlet_id = 1 WHERE outlet_id IS NULL;

-- 6. Indexes for high-throughput multi-tenant isolation
CREATE INDEX IF NOT EXISTS idx_bills_tenant_outlet ON bills(tenant_id, outlet_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_menu_items_tenant ON menu_items(tenant_id, category_id);
CREATE INDEX IF NOT EXISTS idx_categories_tenant ON categories(tenant_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_dining_tables_tenant_outlet ON dining_tables(tenant_id, outlet_id);
CREATE INDEX IF NOT EXISTS idx_shifts_tenant_outlet ON cashier_shifts(tenant_id, outlet_id, status);
CREATE INDEX IF NOT EXISTS idx_expenses_tenant_outlet ON cafe_expenses(tenant_id, outlet_id, expense_date DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_tenant_outlet ON inventory_items(tenant_id, outlet_id);
CREATE INDEX IF NOT EXISTS idx_outlets_tenant ON outlets(tenant_id, is_active);
CREATE INDEX IF NOT EXISTS idx_users_tenant ON users(tenant_id, role);
