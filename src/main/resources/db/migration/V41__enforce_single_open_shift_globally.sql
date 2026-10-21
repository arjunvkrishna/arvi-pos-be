-- Bills do not identify a register, so the current POS can only reconcile
-- payments safely when there is one open drawer shift across the whole system.
-- Fail with an actionable error rather than silently closing or changing any
-- existing shift data if legacy rows violate that rule.
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM cashier_shifts WHERE status = 'OPEN') > 1 THEN
        RAISE EXCEPTION 'Cannot enforce one open cashier shift: multiple shifts are currently OPEN. Close all but the correct active shift, then retry the migration.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_single_open_cashier_shift
    ON cashier_shifts (status)
    WHERE status = 'OPEN';
