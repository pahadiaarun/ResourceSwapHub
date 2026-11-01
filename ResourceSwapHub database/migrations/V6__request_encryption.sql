-- V6: Request encryption session management

CREATE TABLE encryption_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_key VARCHAR(64) NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    client_public_key VARCHAR(500),
    server_public_key VARCHAR(500),
    key_id VARCHAR(100) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_encryption_sessions_key_id ON encryption_sessions (key_id) WHERE active = TRUE;
CREATE INDEX idx_encryption_sessions_user ON encryption_sessions (user_id) WHERE active = TRUE;
CREATE INDEX idx_encryption_sessions_expires ON encryption_sessions (expires_at) WHERE active = TRUE;

CREATE TABLE encryption_replay_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id VARCHAR(100) NOT NULL UNIQUE,
    key_id VARCHAR(100) NOT NULL,
    used_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_encryption_replay_expires ON encryption_replay_cache (expires_at);
