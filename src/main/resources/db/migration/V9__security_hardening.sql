-- ── SmartHire · V9__security_hardening.sql ──

-- Account lockout tracking
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS failed_login_attempts INT         NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_failed_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS email_verified        BOOLEAN     NOT NULL DEFAULT FALSE;

-- Password history — prevent reuse of last 5 passwords
CREATE TABLE IF NOT EXISTS password_history (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    password_hash VARCHAR(60) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_pwd_history_user ON password_history(user_id, created_at DESC);

-- GDPR erasure log — immutable audit trail of data deletion requests
CREATE TABLE IF NOT EXISTS gdpr_erasure_log (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    requested_by    UUID,                               -- null if self-deletion
    subject_email   VARCHAR(255) NOT NULL,              -- hashed after erasure
    subject_id      UUID        NOT NULL,
    reason          VARCHAR(500),
    erased_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    erased_tables   JSONB       NOT NULL DEFAULT '[]'
);
