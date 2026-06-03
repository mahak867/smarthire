-- ── SmartHire · V3__create_applications.sql ──
CREATE TYPE application_status AS ENUM (
    'APPLIED','SCREENING','SHORTLISTED','INTERVIEW','OFFERED','REJECTED','WITHDRAWN'
);

CREATE TABLE applications (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id           UUID NOT NULL REFERENCES jobs(id) ON DELETE RESTRICT,
    candidate_id     UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    resume_url       VARCHAR(1000) NOT NULL,
    cover_letter     TEXT,
    status           application_status NOT NULL DEFAULT 'APPLIED',
    ai_score         NUMERIC(5,2),
    ai_summary       TEXT,
    skill_match_pct  NUMERIC(5,2),
    keyword_matches  JSONB,
    scoring_complete BOOLEAN NOT NULL DEFAULT FALSE,
    notes            TEXT,
    applied_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_application UNIQUE (job_id, candidate_id),
    CONSTRAINT chk_ai_score   CHECK (ai_score IS NULL OR (ai_score >= 0 AND ai_score <= 100))
);

CREATE TRIGGER trg_applications_updated_at
    BEFORE UPDATE ON applications
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
