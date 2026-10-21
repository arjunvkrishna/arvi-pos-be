-- V21__employee_leaves_quota_and_notifications.sql
-- Add paid_leaves_quota and casual_leaves_quota to employees table
ALTER TABLE employees ADD COLUMN IF NOT EXISTS paid_leaves_quota INT DEFAULT 12;
ALTER TABLE employees ADD COLUMN IF NOT EXISTS casual_leaves_quota INT DEFAULT 12;

-- Create system_notifications table for manager alerts & stock notices
CREATE TABLE IF NOT EXISTS system_notifications (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    reference_id BIGINT,
    status VARCHAR(50) NOT NULL DEFAULT 'UNREAD',
    created_by VARCHAR(100),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_system_notifications_status ON system_notifications (status);
CREATE INDEX IF NOT EXISTS idx_system_notifications_created_at ON system_notifications (created_at DESC);
