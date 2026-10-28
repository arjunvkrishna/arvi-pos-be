CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS shift_schedules (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    role_note VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED, COMPLETED, CANCELLED
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_shift_time CHECK (ends_at > starts_at)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'no_overlapping_shifts'
    ) THEN
        ALTER TABLE shift_schedules
        ADD CONSTRAINT no_overlapping_shifts
        EXCLUDE USING gist (
            employee_id WITH =,
            tstzrange(starts_at, ends_at, '[)') WITH &&
        )
        WHERE (status <> 'CANCELLED');
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_shift_schedules_emp ON shift_schedules(employee_id);
CREATE INDEX IF NOT EXISTS idx_shift_schedules_range ON shift_schedules(starts_at, ends_at);

CREATE TABLE IF NOT EXISTS tip_pool_distributions (
    id BIGSERIAL PRIMARY KEY,
    distribution_date DATE NOT NULL UNIQUE, -- Business date of tip pool
    total_tips NUMERIC(10,2) NOT NULL,
    total_hours NUMERIC(6,2) NOT NULL,
    rate_per_hour NUMERIC(10,4) NOT NULL,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tip_pool_employee_shares (
    id BIGSERIAL PRIMARY KEY,
    distribution_id BIGINT NOT NULL REFERENCES tip_pool_distributions(id) ON DELETE CASCADE,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    hours_worked NUMERIC(6,2) NOT NULL,
    tip_amount NUMERIC(10,2) NOT NULL,
    paid BOOLEAN NOT NULL DEFAULT FALSE,
    paid_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_tip_shares_dist ON tip_pool_employee_shares(distribution_id);
CREATE INDEX IF NOT EXISTS idx_tip_shares_emp ON tip_pool_employee_shares(employee_id);
