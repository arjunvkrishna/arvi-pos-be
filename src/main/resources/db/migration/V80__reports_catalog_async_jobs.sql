CREATE TABLE report_export_jobs (
    id UUID PRIMARY KEY,
    report_id VARCHAR(50) NOT NULL,
    format VARCHAR(10) NOT NULL, -- CSV, XLSX, PDF
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    filter_params JSONB,
    download_key VARCHAR(255),
    error_message TEXT,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_report_export_jobs_status ON report_export_jobs(status);
CREATE INDEX idx_report_export_jobs_created ON report_export_jobs(created_at);

CREATE TABLE report_export_data (
    job_id UUID PRIMARY KEY REFERENCES report_export_jobs(id) ON DELETE CASCADE,
    file_data BYTEA NOT NULL
);
