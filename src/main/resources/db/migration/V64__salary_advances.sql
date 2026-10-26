-- ============================================================
-- NERAM POS - Salary advances (V64)
-- A manager can give a staff member part of the month's pay early. Each advance is a row;
-- the salary's balance to pay is net salary minus the advances. Additive only.
-- ============================================================
CREATE TABLE IF NOT EXISTS salary_advances (
    id          BIGSERIAL PRIMARY KEY,
    salary_id   BIGINT        NOT NULL REFERENCES salaries(id) ON DELETE CASCADE,
    amount      NUMERIC(12,2) NOT NULL,
    given_date  DATE          NOT NULL,
    given_by    VARCHAR(100),
    note        VARCHAR(255),
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_salary_advances_salary ON salary_advances(salary_id);
