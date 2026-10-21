-- V42__recurring_expenses_and_templates.sql
-- Add recurring fields to cafe_expenses and create expense_templates table

-- 1. Enhance cafe_expenses table
ALTER TABLE cafe_expenses ADD COLUMN IF NOT EXISTS vendor_or_payee VARCHAR(255);
ALTER TABLE cafe_expenses ADD COLUMN IF NOT EXISTS payment_method VARCHAR(100);
ALTER TABLE cafe_expenses ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'PAID';
ALTER TABLE cafe_expenses ADD COLUMN IF NOT EXISTS due_date DATE;
ALTER TABLE cafe_expenses ADD COLUMN IF NOT EXISTS template_id BIGINT;
ALTER TABLE cafe_expenses ADD COLUMN IF NOT EXISTS is_recurring BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Create expense_templates table
CREATE TABLE IF NOT EXISTS expense_templates (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    frequency VARCHAR(50) NOT NULL DEFAULT 'MONTHLY',
    due_day INT NOT NULL DEFAULT 1,
    vendor_or_payee VARCHAR(255),
    payment_method VARCHAR(100),
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_generated_date DATE,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Index on template_id in cafe_expenses
CREATE INDEX IF NOT EXISTS idx_cafe_expenses_template_id ON cafe_expenses(template_id);
CREATE INDEX IF NOT EXISTS idx_cafe_expenses_month_year ON cafe_expenses(year, month);
CREATE INDEX IF NOT EXISTS idx_expense_templates_is_active ON expense_templates(is_active);
