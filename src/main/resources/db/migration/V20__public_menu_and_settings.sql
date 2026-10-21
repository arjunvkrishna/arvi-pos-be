-- V12__public_menu_and_settings.sql
-- Create App Settings table for global configuration toggles
CREATE TABLE IF NOT EXISTS app_settings (
    setting_key VARCHAR(64) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) DEFAULT 'SYSTEM'
);

-- Seed default public menu setting
INSERT INTO app_settings (setting_key, setting_value, updated_at, updated_by)
VALUES ('PUBLIC_MENU_ENABLED', 'true', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (setting_key) DO NOTHING;

-- Safe creation of PostgreSQL read-only user for menu access
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'neram_readonly') THEN
        BEGIN
            CREATE ROLE neram_readonly WITH NOLOGIN;
        EXCEPTION WHEN insufficient_privilege THEN
            RAISE NOTICE 'Insufficient privilege to create role neram_readonly, skipping role creation';
        END;
    END IF;
END
$$;

-- Grant restricted permissions for read-only user
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'neram_readonly') THEN
        BEGIN
            EXECUTE format('GRANT CONNECT ON DATABASE %I TO neram_readonly', current_database());
            GRANT USAGE ON SCHEMA public TO neram_readonly;
            GRANT SELECT (id, name, price, category_id, is_veg, is_available, description, active) ON menu_items TO neram_readonly;
            GRANT SELECT (id, name, icon, sort_order, active) ON categories TO neram_readonly;
            GRANT SELECT (setting_key, setting_value) ON app_settings TO neram_readonly;
        EXCEPTION WHEN others THEN
            RAISE NOTICE 'Unable to grant permissions to neram_readonly: %', SQLERRM;
        END;
    END IF;
END
$$;
