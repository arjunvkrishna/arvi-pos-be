-- ============================================================
-- NERAM POS - Stock count (physical count against the book figure) (V66)
-- Additive only. A count is a snapshot of book quantities, the counted figures, and when applied one ADJUSTMENT per differing item.
-- ============================================================
CREATE TABLE IF NOT EXISTS stock_counts (
    id          BIGSERIAL PRIMARY KEY,
    status      VARCHAR(12)  NOT NULL DEFAULT 'DRAFT',
    scope       VARCHAR(60),
    note        VARCHAR(300),
    created_by  VARCHAR(100),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    applied_by  VARCHAR(100),
    applied_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stock_count_lines (
    id              BIGSERIAL PRIMARY KEY,
    count_id        BIGINT           NOT NULL REFERENCES stock_counts(id) ON DELETE CASCADE,
    item_id         BIGINT           NOT NULL,
    item_name       VARCHAR(255)     NOT NULL,
    unit            VARCHAR(50),
    book_qty        DOUBLE PRECISION NOT NULL,
    counted_qty     DOUBLE PRECISION,
    cost_per_unit   NUMERIC(12,2)    NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_stock_count_lines_count ON stock_count_lines(count_id);
