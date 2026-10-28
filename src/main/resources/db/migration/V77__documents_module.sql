CREATE TABLE IF NOT EXISTS document_categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS documents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    category_id BIGINT NOT NULL REFERENCES document_categories(id),
    tags TEXT[] DEFAULT '{}',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_documents_category ON documents(category_id);

CREATE TABLE IF NOT EXISTS document_attachments (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(255),
    uploaded_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_doc_attachments_doc ON document_attachments(document_id);

CREATE TABLE IF NOT EXISTS document_attachment_data (
    attachment_id BIGINT PRIMARY KEY REFERENCES document_attachments(id) ON DELETE CASCADE,
    file_data BYTEA NOT NULL
);

-- Initial default document categories
INSERT INTO document_categories (name) VALUES 
('Standard Operating Procedures'),
('Employment Contracts'),
('Kitchen & Food Safety'),
('Vendor Agreements')
ON CONFLICT (name) DO NOTHING;

-- Supplier delivery tracking columns for supplier analytics
ALTER TABLE supplier_invoices
    ADD COLUMN IF NOT EXISTS expected_delivery_date DATE,
    ADD COLUMN IF NOT EXISTS delivery_date DATE;
