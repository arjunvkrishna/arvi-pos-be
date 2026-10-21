-- ============================================================
-- NERAM POS System - Full PostgreSQL Database Schema & Seed Data
-- Flyway Migration V1
-- ============================================================

-- Users (Authentication)
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    employee_id BIGINT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Employees
CREATE TABLE IF NOT EXISTS employees (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    email VARCHAR(100),
    role VARCHAR(50) NOT NULL,
    department VARCHAR(100),
    designation VARCHAR(100),
    basic_salary DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    joined_date DATE,
    address TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Categories (Menu categories)
CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    icon VARCHAR(100) DEFAULT 'fa-utensils',
    sort_order INT DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Menu Items
CREATE TABLE IF NOT EXISTS menu_items (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category_id BIGINT NOT NULL,
    price DOUBLE PRECISION NOT NULL,
    description TEXT,
    image_url VARCHAR(500),
    is_veg BOOLEAN NOT NULL DEFAULT TRUE,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_menu_category FOREIGN KEY (category_id) REFERENCES categories(id)
);

-- Bills
CREATE TABLE IF NOT EXISTS bills (
    id BIGSERIAL PRIMARY KEY,
    bill_number VARCHAR(100) NOT NULL UNIQUE,
    table_number VARCHAR(100),
    customer_name VARCHAR(255),
    subtotal DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    discount_percent DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    discount_amount DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    tax_percent DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    tax_amount DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    net_amount DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    payment_mode VARCHAR(50) NOT NULL DEFAULT 'CASH',
    status VARCHAR(50) NOT NULL DEFAULT 'PAID',
    notes TEXT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    printed_at TIMESTAMP NULL,
    CONSTRAINT fk_bill_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

-- Bill Items
CREATE TABLE IF NOT EXISTS bill_items (
    id BIGSERIAL PRIMARY KEY,
    bill_id BIGINT NOT NULL,
    menu_item_id BIGINT,
    item_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    price DOUBLE PRECISION NOT NULL,
    subtotal DOUBLE PRECISION NOT NULL,
    CONSTRAINT fk_bill_items_bill FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE,
    CONSTRAINT fk_bill_items_menu FOREIGN KEY (menu_item_id) REFERENCES menu_items(id)
);

-- Inventory Items
CREATE TABLE IF NOT EXISTS inventory_items (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    unit VARCHAR(50) NOT NULL DEFAULT 'pcs',
    quantity DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    min_quantity DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    cost_per_unit DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    supplier VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Cafe Expenses
CREATE TABLE IF NOT EXISTS cafe_expenses (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    expense_date DATE NOT NULL,
    month INT,
    year INT,
    paid_by VARCHAR(100),
    receipt_ref VARCHAR(100),
    notes TEXT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_expense_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

-- Salaries
CREATE TABLE IF NOT EXISTS salaries (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    month INT NOT NULL,
    year INT NOT NULL,
    basic DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    allowances DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    deductions DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    net_salary DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    paid BOOLEAN NOT NULL DEFAULT FALSE,
    paid_date DATE,
    payment_mode VARCHAR(50) DEFAULT 'CASH',
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_salary_emp FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT uk_salary_emp_month_year UNIQUE (employee_id, month, year)
);

-- Attendance
CREATE TABLE IF NOT EXISTS attendance (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    date DATE NOT NULL,
    check_in TIME,
    check_out TIME,
    status VARCHAR(50) NOT NULL DEFAULT 'PRESENT',
    notes TEXT,
    marked_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_att_emp FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_att_marked_by FOREIGN KEY (marked_by) REFERENCES users(id),
    CONSTRAINT uk_att_emp_date UNIQUE (employee_id, date)
);

-- Leave Requests
CREATE TABLE IF NOT EXISTS leave_requests (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    from_date DATE NOT NULL,
    "to_date" DATE NOT NULL,
    leave_type VARCHAR(50) NOT NULL DEFAULT 'CASUAL',
    reason TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP,
    review_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_leave_emp FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_leave_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users(id)
);

-- Audit Logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id BIGINT,
    old_value TEXT,
    new_value TEXT,
    performed_by BIGINT,
    performed_by_name VARCHAR(100),
    ip_address VARCHAR(100),
    performed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user FOREIGN KEY (performed_by) REFERENCES users(id)
);

-- ============================================================
-- ============================================================
-- SEED DATA & BOOTSTRAPPING
-- ============================================================
-- Initial admin account creation and optional demo seeding (categories, menu,
-- inventory, suppliers, demo staff) are handled dynamically by application DataInitializer
-- controlled by the SEED_DEMO_DATA environment variable.
