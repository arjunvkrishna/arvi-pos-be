-- V46: Promotion Management and Automatic Billing Snapshot

CREATE TABLE IF NOT EXISTS promotions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    discount_percentage DOUBLE PRECISION NOT NULL,
    start_date_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    end_date_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    created_by BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_promotions_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_promotions_active_dates ON promotions(is_active, start_date_time, end_date_time);

-- Add promotion snapshot columns to bills
ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS promotion_id BIGINT,
    ADD COLUMN IF NOT EXISTS promotion_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS promotion_percentage DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS promotion_discount_amount NUMERIC(12,2) DEFAULT 0.00;

ALTER TABLE bills
    DROP CONSTRAINT IF EXISTS fk_bills_promotion;

ALTER TABLE bills
    ADD CONSTRAINT fk_bills_promotion FOREIGN KEY (promotion_id) REFERENCES promotions(id) ON DELETE SET NULL;
