-- ── SmartHire · V8__add_interview_fields_and_file_uploads.sql ──
-- Add missing columns to interviews (meeting_link, notes)
ALTER TABLE interviews
    ADD COLUMN IF NOT EXISTS meeting_link VARCHAR(500),
    ADD COLUMN IF NOT EXISTS notes        TEXT;

-- File uploads table for audit trail of all uploaded resumes
CREATE TABLE IF NOT EXISTS file_uploads (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uploader_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    object_key      VARCHAR(1000) NOT NULL UNIQUE,
    original_name   VARCHAR(500),
    file_size_bytes BIGINT,
    content_type    VARCHAR(100),
    bucket          VARCHAR(200) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_file_uploads_uploader ON file_uploads(uploader_id);
CREATE INDEX IF NOT EXISTS idx_file_uploads_created  ON file_uploads(created_at DESC);
