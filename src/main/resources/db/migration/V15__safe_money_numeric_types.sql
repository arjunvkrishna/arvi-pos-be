-- ============================================================
-- NERAM POS System - Safe Money Numerical Precision (NUMERIC(12,2))
-- Flyway Migration V15 (PostgreSQL Compatible)
-- ============================================================

-- 1. Menu Items
ALTER TABLE menu_items 
    ALTER COLUMN price TYPE NUMERIC(12,2) USING ROUND(CAST(price AS NUMERIC), 2);

-- 2. Bills
ALTER TABLE bills 
    ALTER COLUMN subtotal TYPE NUMERIC(12,2) USING ROUND(CAST(subtotal AS NUMERIC), 2),
    ALTER COLUMN discount_amount TYPE NUMERIC(12,2) USING ROUND(CAST(discount_amount AS NUMERIC), 2),
    ALTER COLUMN tax_amount TYPE NUMERIC(12,2) USING ROUND(CAST(tax_amount AS NUMERIC), 2),
    ALTER COLUMN net_amount TYPE NUMERIC(12,2) USING ROUND(CAST(net_amount AS NUMERIC), 2);

-- Round-off and GST amounts if already created in V14
ALTER TABLE bills 
    ALTER COLUMN cgst_amount TYPE NUMERIC(12,2) USING ROUND(CAST(cgst_amount AS NUMERIC), 2),
    ALTER COLUMN sgst_amount TYPE NUMERIC(12,2) USING ROUND(CAST(sgst_amount AS NUMERIC), 2),
    ALTER COLUMN round_off TYPE NUMERIC(12,2) USING ROUND(CAST(round_off AS NUMERIC), 2);

-- 3. Bill Items
ALTER TABLE bill_items 
    ALTER COLUMN price TYPE NUMERIC(12,2) USING ROUND(CAST(price AS NUMERIC), 2),
    ALTER COLUMN subtotal TYPE NUMERIC(12,2) USING ROUND(CAST(subtotal AS NUMERIC), 2);

-- 4. Inventory Items
ALTER TABLE inventory_items 
    ALTER COLUMN cost_per_unit TYPE NUMERIC(12,2) USING ROUND(CAST(cost_per_unit AS NUMERIC), 2);

-- 5. Cafe Expenses
ALTER TABLE cafe_expenses 
    ALTER COLUMN amount TYPE NUMERIC(12,2) USING ROUND(CAST(amount AS NUMERIC), 2);

-- 6. Salaries
ALTER TABLE salaries 
    ALTER COLUMN basic TYPE NUMERIC(12,2) USING ROUND(CAST(basic AS NUMERIC), 2),
    ALTER COLUMN allowances TYPE NUMERIC(12,2) USING ROUND(CAST(allowances AS NUMERIC), 2),
    ALTER COLUMN deductions TYPE NUMERIC(12,2) USING ROUND(CAST(deductions AS NUMERIC), 2),
    ALTER COLUMN net_salary TYPE NUMERIC(12,2) USING ROUND(CAST(net_salary AS NUMERIC), 2);

-- 7. Supplier Invoices & Payments
ALTER TABLE supplier_invoices 
    ALTER COLUMN total_amount TYPE NUMERIC(12,2) USING ROUND(CAST(total_amount AS NUMERIC), 2),
    ALTER COLUMN paid_amount TYPE NUMERIC(12,2) USING ROUND(CAST(paid_amount AS NUMERIC), 2),
    ALTER COLUMN outstanding_amount TYPE NUMERIC(12,2) USING ROUND(CAST(outstanding_amount AS NUMERIC), 2);

ALTER TABLE supplier_invoice_items 
    ALTER COLUMN unit_price TYPE NUMERIC(12,2) USING ROUND(CAST(unit_price AS NUMERIC), 2),
    ALTER COLUMN total_price TYPE NUMERIC(12,2) USING ROUND(CAST(total_price AS NUMERIC), 2);

ALTER TABLE supplier_payments 
    ALTER COLUMN amount TYPE NUMERIC(12,2) USING ROUND(CAST(amount AS NUMERIC), 2);

-- 8. Inventory Transactions
ALTER TABLE inventory_transactions 
    ALTER COLUMN unit_price TYPE NUMERIC(12,2) USING ROUND(CAST(unit_price AS NUMERIC), 2);
