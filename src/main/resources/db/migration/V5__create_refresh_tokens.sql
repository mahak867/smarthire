-- ── SmartHire · V5__create_refresh_tokens.sql ──
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    ip_address  INET,
    user_agent  VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_user     ON refresh_tokens(user_id);
CREATE INDEX ix_refresh_tokens_hash     ON refresh_tokens(token_hash);
CREATE INDEX ix_refresh_tokens_expires  ON refresh_tokens(expires_at);
