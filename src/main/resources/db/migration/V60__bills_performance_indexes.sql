-- Performance indexes for hot read paths. Additive and idempotent; no data changes.
-- bill_items.bill_id: Bill.items is fetched EAGERly, so every bill load issues "WHERE bill_id = ?"
-- (Postgres does not index foreign-key columns automatically).
CREATE INDEX IF NOT EXISTS idx_bill_items_bill_id ON bill_items(bill_id);
-- bills.status + created_at: /api/bills/active (polled every 3s per POS terminal) filters by status.
CREATE INDEX IF NOT EXISTS idx_bills_status_created_at ON bills(status, created_at);
-- bills.created_at: daily/monthly bill log, dashboards and reports filter and sort by created_at.
CREATE INDEX IF NOT EXISTS idx_bills_created_at ON bills(created_at DESC);

