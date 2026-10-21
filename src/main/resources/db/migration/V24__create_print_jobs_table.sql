-- ============================================================
-- Migration V24: Persistent Print Jobs Table for Authoritative Print Queue
-- ============================================================

CREATE TABLE IF NOT EXISTS print_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(100) NOT NULL UNIQUE,
    bill_id BIGINT,
    bill_number VARCHAR(100),
    job_type VARCHAR(50) NOT NULL,
    target_printer_host VARCHAR(150) NOT NULL,
    target_printer_port INT NOT NULL,
    payload BYTEA,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_eligible_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at TIMESTAMP NULL,
    sent_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    lease_expires_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_by_bridge_id VARCHAR(100),
    last_error_message TEXT,
    idempotency_key VARCHAR(150),
    bytes_printed INT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_print_jobs_status_eligible ON print_jobs (status, next_eligible_at);
CREATE INDEX IF NOT EXISTS idx_print_jobs_bill_id ON print_jobs (bill_id);
CREATE INDEX IF NOT EXISTS idx_print_jobs_idempotency_key ON print_jobs (idempotency_key);
CREATE INDEX IF NOT EXISTS idx_print_jobs_created_at ON print_jobs (created_at);
CREATE INDEX IF NOT EXISTS idx_print_jobs_lease_expires ON print_jobs (status, lease_expires_at);
