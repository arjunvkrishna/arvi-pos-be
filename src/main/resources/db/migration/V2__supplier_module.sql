-- ============================================================
-- NERAM POS System - Supplier & Partial Payment Module
-- Flyway Migration V2 (PostgreSQL Compatible)
-- ============================================================

-- Supplier Master
CREATE TABLE IF NOT EXISTS suppliers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    contact_person VARCHAR(255),
    phone VARCHAR(50),
    email VARCHAR(150),
    address TEXT,
    gst_number VARCHAR(50),
    payment_terms INT NOT NULL DEFAULT 30,   -- days
    category VARCHAR(100),                    -- e.g. Vegetables, Dairy, Meat
    bank_name VARCHAR(150),
    bank_account VARCHAR(100),
    bank_ifsc VARCHAR(20),
    upi_id VARCHAR(150),
    notes TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Supplier Invoices
CREATE TABLE IF NOT EXISTS supplier_invoices (
    id BIGSERIAL PRIMARY KEY,
    supplier_id BIGINT NOT NULL,
    invoice_number VARCHAR(150) NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE,
    total_amount DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    paid_amount DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    outstanding_amount DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    status VARCHAR(50) NOT NULL DEFAULT 'UNPAID',  -- UNPAID, PARTIAL, PAID, OVERDUE
    description TEXT,
    items_received TEXT,                             -- JSON snapshot of goods received
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inv_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_inv_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT uk_supplier_invoice UNIQUE (supplier_id, invoice_number)
);

-- Supplier Payments (each partial / full payment)
CREATE TABLE IF NOT EXISTS supplier_payments (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    payment_date DATE NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    payment_mode VARCHAR(50) NOT NULL DEFAULT 'CASH',  -- CASH, UPI, BANK_TRANSFER, CHEQUE
    reference_number VARCHAR(150),
    notes TEXT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pay_invoice FOREIGN KEY (invoice_id) REFERENCES supplier_invoices(id) ON DELETE CASCADE,
    CONSTRAINT fk_pay_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_pay_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

-- Sample suppliers seeding is handled dynamically by DataInitializer when SEED_DEMO_DATA=true
