-- ============================================================
-- NERAM POS System - Dining Tables Management
-- Flyway Migration V11 (PostgreSQL Compatible)
-- ============================================================

CREATE TABLE IF NOT EXISTS dining_tables (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed default dining tables if empty
INSERT INTO dining_tables (name, active, display_order)
VALUES
    ('T-01', TRUE, 1),
    ('T-02', TRUE, 2),
    ('T-03', TRUE, 3),
    ('T-04', TRUE, 4),
    ('T-05', TRUE, 5),
    ('T-06', TRUE, 6),
    ('T-07', TRUE, 7),
    ('T-08', TRUE, 8),
    ('Takeaway', TRUE, 9)
ON CONFLICT (name) DO NOTHING;
