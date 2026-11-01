-- V1: identity, sessions, uploaded media.
-- All timestamps are UTC. UUIDs use PostgreSQL's built-in gen_random_uuid().

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(320) NOT NULL,
    password_hash   VARCHAR(100),
    google_subject  VARCHAR(255),
    display_name    VARCHAR(80)  NOT NULL,
    avatar_url      VARCHAR(500),
    city            VARCHAR(80),
    locality        VARCHAR(120),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_email_not_blank CHECK (btrim(email) <> ''),
    CONSTRAINT ck_users_name_not_blank CHECK (btrim(display_name) <> '')
);

CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));
CREATE UNIQUE INDEX uq_users_google_subject
    ON users (google_subject)
    WHERE google_subject IS NOT NULL;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    user_agent      VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_active
    ON refresh_tokens (user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE media_assets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    storage_key     VARCHAR(255) NOT NULL UNIQUE,
    original_name   VARCHAR(255) NOT NULL,
    content_type    VARCHAR(80) NOT NULL,
    size_bytes      BIGINT NOT NULL,
    purpose         VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_media_size_positive CHECK (size_bytes > 0),
    CONSTRAINT ck_media_purpose CHECK (purpose IN ('LISTING', 'AVATAR'))
);

CREATE INDEX idx_media_assets_owner ON media_assets (owner_id, created_at DESC);
