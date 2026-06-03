-- ── SmartHire · V4__create_interviews.sql ──
CREATE TYPE interview_type   AS ENUM ('PHONE','VIDEO','ONSITE');
CREATE TYPE interview_status AS ENUM ('SCHEDULED','COMPLETED','CANCELLED','NO_SHOW');

CREATE TABLE interviews (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id     UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    scheduled_at       TIMESTAMPTZ NOT NULL,
    duration_minutes   INT NOT NULL DEFAULT 60,
    type               interview_type NOT NULL DEFAULT 'VIDEO',
    interviewer_id     UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status             interview_status NOT NULL DEFAULT 'SCHEDULED',
    feedback           TEXT,
    rating             SMALLINT CHECK (rating BETWEEN 1 AND 5),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_interviews_updated_at
    BEFORE UPDATE ON interviews
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
