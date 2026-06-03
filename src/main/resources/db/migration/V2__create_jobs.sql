-- ── SmartHire · V2__create_jobs.sql ──
CREATE TYPE employment_type AS ENUM ('FULL_TIME','PART_TIME','CONTRACT','INTERNSHIP');
CREATE TYPE job_status      AS ENUM ('DRAFT','OPEN','CLOSED','ARCHIVED');

CREATE TABLE jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(200) NOT NULL,
    description     TEXT NOT NULL,
    requirements    TEXT NOT NULL,
    department      VARCHAR(100),
    location        VARCHAR(200),
    employment_type employment_type NOT NULL DEFAULT 'FULL_TIME',
    salary_min      NUMERIC(12,2),
    salary_max      NUMERIC(12,2),
    currency        VARCHAR(3) NOT NULL DEFAULT 'INR',
    status          job_status NOT NULL DEFAULT 'DRAFT',
    posted_by       UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    deadline        DATE,
    views_count     BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_salary_range CHECK (salary_max IS NULL OR salary_min IS NULL OR salary_max >= salary_min)
);

CREATE TRIGGER trg_jobs_updated_at
    BEFORE UPDATE ON jobs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
