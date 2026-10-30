-- When the payment was confirmed. With the time the kitchen ticket was printed it gives how long guests stayed (analytics: dine-in time).
ALTER TABLE bills ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP;

-- Bills paid before this column existed: the audit log recorded the moment the payment was confirmed (same clock as kot_printed_at).
UPDATE bills b SET paid_at = a.first_confirmed
FROM (SELECT entity_id, MIN(performed_at) AS first_confirmed
        FROM audit_logs
       WHERE entity_type = 'BILL' AND action IN ('CONFIRM_PAYMENT', 'APPROVE_AND_PRINT_BILL') AND entity_id IS NOT NULL
       GROUP BY entity_id) a
WHERE a.entity_id = b.id AND b.paid_at IS NULL AND b.status IN ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED');
