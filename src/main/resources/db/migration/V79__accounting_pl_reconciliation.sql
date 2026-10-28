CREATE TABLE bank_statements (
    id BIGSERIAL PRIMARY KEY,
    description VARCHAR(100) NOT NULL,
    import_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_count INT NOT NULL,
    imported_by BIGINT REFERENCES users(id)
);

CREATE TABLE bank_statement_rows (
    id BIGSERIAL PRIMARY KEY,
    statement_id BIGINT NOT NULL REFERENCES bank_statements(id) ON DELETE CASCADE,
    transaction_date DATE NOT NULL,
    description TEXT,
    amount NUMERIC(10,2) NOT NULL,
    balance NUMERIC(10,2),
    ref_no VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'UNMATCHED',
    matched_expense_id BIGINT REFERENCES cafe_expenses(id),
    matched_at TIMESTAMPTZ,
    import_row_hash VARCHAR(64) UNIQUE NOT NULL
);
CREATE INDEX idx_bank_statement_rows_stmt ON bank_statement_rows(statement_id);
CREATE INDEX idx_bank_statement_rows_status ON bank_statement_rows(status);
