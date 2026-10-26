-- Control database: the cafe registry and platform (SaaS owner) accounts.
-- Cafe business data never lives here; each cafe has its own database.

CREATE TABLE IF NOT EXISTS tenants (
    id                BIGSERIAL PRIMARY KEY,
    code              VARCHAR(40)  NOT NULL UNIQUE,
    name              VARCHAR(255) NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PROVISIONING',
    subscription_plan VARCHAR(30)  NOT NULL DEFAULT 'PRO',
    max_outlets       INTEGER      NOT NULL DEFAULT 5,
    contact_email     VARCHAR(255),
    contact_phone     VARCHAR(50),
    address           TEXT,
    db_host           VARCHAR(255) NOT NULL,
    db_port           INTEGER      NOT NULL DEFAULT 5432,
    db_name           VARCHAR(63)  NOT NULL UNIQUE,
    db_user           VARCHAR(63)  NOT NULL UNIQUE,
    db_password_enc   TEXT         NOT NULL,
    bridge_token_enc  TEXT,
    schema_version    VARCHAR(50),
    migrated_at       TIMESTAMPTZ,
    last_error        TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT tenants_status_chk CHECK (status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS platform_admins (
    id                   BIGSERIAL PRIMARY KEY,
    username             VARCHAR(100) NOT NULL UNIQUE,
    password_hash        VARCHAR(255) NOT NULL,
    full_name            VARCHAR(255),
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS tenant_events (
    id         BIGSERIAL PRIMARY KEY,
    tenant_id  BIGINT,
    event      VARCHAR(60) NOT NULL,
    actor      VARCHAR(100),
    detail     TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tenant_events_tenant ON tenant_events (tenant_id, id DESC);
