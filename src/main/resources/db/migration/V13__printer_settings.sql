-- ============================================================
-- NERAM POS System - Thermal Printer Configuration
-- Flyway Migration V13 (PostgreSQL Compatible)
-- ============================================================

CREATE TABLE IF NOT EXISTS printer_settings (
    id BIGINT PRIMARY KEY,
    host VARCHAR(100) NOT NULL DEFAULT '192.168.1.47',
    port INT NOT NULL DEFAULT 9100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    auto_cut BOOLEAN NOT NULL DEFAULT TRUE,
    paper_width_mm INT NOT NULL DEFAULT 80,
    header_title VARCHAR(100) NOT NULL DEFAULT 'NERAM',
    subheader VARCHAR(150) NOT NULL DEFAULT 'Where time tastes better',
    footer_message VARCHAR(150) NOT NULL DEFAULT 'THANK YOU! VISIT AGAIN',
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed default printer setting row (id = 1)
INSERT INTO printer_settings (id, host, port, enabled, auto_cut, paper_width_mm, header_title, subheader, footer_message, updated_at)
VALUES (1, '192.168.1.47', 9100, TRUE, TRUE, 80, 'NERAM', 'Where time tastes better', 'THANK YOU! VISIT AGAIN', NOW())
ON CONFLICT (id) DO NOTHING;
