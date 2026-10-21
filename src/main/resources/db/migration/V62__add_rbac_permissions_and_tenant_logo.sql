-- V62__add_rbac_permissions_and_tenant_logo.sql
-- Add tenant logo & GST number, and granular user permissions (can_bill, can_manage_expenses, can_view_reports)

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS logo_url TEXT;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS gst_number VARCHAR(64);

ALTER TABLE users ADD COLUMN IF NOT EXISTS can_bill BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS can_manage_expenses BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS can_view_reports BOOLEAN NOT NULL DEFAULT FALSE;

-- Elevate permissions for existing admins and managers
UPDATE users SET can_bill = TRUE, can_manage_expenses = TRUE, can_view_reports = TRUE
WHERE role IN ('SUPER_ADMIN', 'ADMIN', 'CAFE_ADMIN', 'GENERAL_MANAGER', 'MANAGER');
