-- V33__ensure_readonly_user_and_grants.sql
-- Ensure read-only user and table privileges exist for public menu without needing host-level init scripts.

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'neram_readonly') THEN
        BEGIN
            CREATE ROLE neram_readonly WITH NOLOGIN;
        EXCEPTION WHEN insufficient_privilege THEN
            RAISE NOTICE 'Insufficient privilege to create role neram_readonly, skipping role creation';
        END;
    END IF;

    BEGIN
        EXECUTE format('GRANT CONNECT ON DATABASE %I TO neram_readonly', current_database());
        GRANT USAGE ON SCHEMA public TO neram_readonly;
        REVOKE CREATE ON SCHEMA public FROM neram_readonly;
        REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM neram_readonly;

        GRANT SELECT (id, name, price, category_id, is_veg, is_available, description, active) ON menu_items TO neram_readonly;
        GRANT SELECT (id, name, icon, sort_order, active) ON categories TO neram_readonly;
        GRANT SELECT (setting_key, setting_value) ON app_settings TO neram_readonly;
    EXCEPTION WHEN others THEN
        RAISE NOTICE 'Unable to grant permissions to neram_readonly: %', SQLERRM;
    END;
END
$$;
