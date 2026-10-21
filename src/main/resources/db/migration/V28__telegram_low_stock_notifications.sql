-- ============================================================
-- Migration V25: Telegram Low-Stock Notification Module
-- ============================================================

-- 1. Telegram Global Settings Table
CREATE TABLE IF NOT EXISTS telegram_settings (
    id BIGSERIAL PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    daily_send_time VARCHAR(8) NOT NULL DEFAULT '09:00',
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Kolkata',
    last_run_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed default settings row (id=1, disabled by default)
INSERT INTO telegram_settings (id, enabled, daily_send_time, timezone)
VALUES (1, FALSE, '09:00', 'Asia/Kolkata')
ON CONFLICT (id) DO NOTHING;

-- 2. Telegram Manager Recipients Table
CREATE TABLE IF NOT EXISTS telegram_recipients (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    telegram_chat_id VARCHAR(100),
    telegram_username VARCHAR(100),
    telegram_first_name VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'NOT_CONFIGURED',
    selected BOOLEAN NOT NULL DEFAULT TRUE,
    link_code VARCHAR(64),
    link_code_expires_at TIMESTAMP,
    verified_at TIMESTAMP,
    last_test_sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_telegram_recipients_user_id ON telegram_recipients(user_id);
CREATE INDEX IF NOT EXISTS idx_telegram_recipients_link_code ON telegram_recipients(link_code);
CREATE INDEX IF NOT EXISTS idx_telegram_recipients_status ON telegram_recipients(status);

-- 3. Telegram Delivery Logs Table (Audit of sent reports and test messages)
CREATE TABLE IF NOT EXISTS telegram_delivery_logs (
    id BIGSERIAL PRIMARY KEY,
    report_date DATE NOT NULL,
    recipient_id BIGINT REFERENCES telegram_recipients(id) ON DELETE SET NULL,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    manager_name VARCHAR(100) NOT NULL,
    chat_id_masked VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    error_description VARCHAR(500),
    items_count INT NOT NULL DEFAULT 0,
    delivery_type VARCHAR(20) NOT NULL DEFAULT 'DAILY_REPORT',
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_telegram_delivery_logs_report_date ON telegram_delivery_logs(report_date);
CREATE INDEX IF NOT EXISTS idx_telegram_delivery_logs_sent_at ON telegram_delivery_logs(sent_at);

-- 4. Telegram Daily Send Locks Table (Distributed deduplication lock)
CREATE TABLE IF NOT EXISTS telegram_daily_send_locks (
    report_date DATE PRIMARY KEY,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);
