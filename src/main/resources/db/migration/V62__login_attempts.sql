-- ============================================================
-- Login lockout: failed login counters per account and per client IP
-- ============================================================
CREATE TABLE IF NOT EXISTS login_attempts (
    id              BIGSERIAL PRIMARY KEY,
    scope           VARCHAR(10)  NOT NULL,            -- 'USER' (lower-cased username) or 'IP'
    attempt_key     VARCHAR(100) NOT NULL,
    failed_count    INTEGER      NOT NULL DEFAULT 0,  -- failures since the last lock / reset
    lock_level      INTEGER      NOT NULL DEFAULT 0,  -- how many times in a row this key has been locked (lock time grows)
    locked_until    TIMESTAMP,
    last_failed_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_login_attempts_scope_key UNIQUE (scope, attempt_key)
);

CREATE INDEX IF NOT EXISTS idx_login_attempts_last_failed ON login_attempts (last_failed_at);
