CREATE TABLE coupons (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    discount_type VARCHAR(20) NOT NULL, -- PERCENTAGE, FIXED_AMOUNT
    discount_value NUMERIC(10,2) NOT NULL,
    min_order_value NUMERIC(10,2) DEFAULT 0,
    max_discount_amount NUMERIC(10,2),
    usage_limit INT,
    usage_limit_per_customer INT DEFAULT 1,
    used_count INT NOT NULL DEFAULT 0,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE coupon_usages (
    id BIGSERIAL PRIMARY KEY,
    coupon_id BIGINT NOT NULL REFERENCES coupons(id),
    customer_id BIGINT REFERENCES customers(id),
    bill_id BIGINT NOT NULL REFERENCES bills(id) ON DELETE CASCADE,
    discount_applied NUMERIC(10,2) NOT NULL,
    used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_coupon_bill UNIQUE (coupon_id, bill_id)
);
CREATE INDEX idx_coupon_usages_cust ON coupon_usages(coupon_id, customer_id);

ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS coupon_id BIGINT REFERENCES coupons(id),
    ADD COLUMN IF NOT EXISTS coupon_discount NUMERIC(10,2);

CREATE TABLE campaigns (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
    audience_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sent_count INT DEFAULT 0,
    scheduled_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
