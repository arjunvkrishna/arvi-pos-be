-- V32: Add must_change_password column to users for first-login force reset flow
ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
