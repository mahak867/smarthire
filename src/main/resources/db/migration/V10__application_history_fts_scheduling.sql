-- ── SmartHire · V10__application_history_fts_scheduling.sql ──

-- Application status change history
CREATE TABLE IF NOT EXISTS application_status_history (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID        NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    from_status     VARCHAR(20),
    to_status       VARCHAR(20) NOT NULL,
    changed_by      UUID        REFERENCES users(id) ON DELETE SET NULL,
    changed_by_name VARCHAR(200),
    note            TEXT,
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_status_history_app ON application_status_history(application_id, changed_at DESC);

-- Full-text search on jobs (GIN index for speed)
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS search_vector tsvector;
CREATE INDEX IF NOT EXISTS idx_jobs_fts ON jobs USING GIN(search_vector);

-- Trigger to auto-update search_vector on insert/update
CREATE OR REPLACE FUNCTION jobs_search_vector_update() RETURNS trigger AS $$
BEGIN
  NEW.search_vector :=
    setweight(to_tsvector('english', coalesce(NEW.title,       '')), 'A') ||
    setweight(to_tsvector('english', coalesce(NEW.department,  '')), 'B') ||
    setweight(to_tsvector('english', coalesce(NEW.description, '')), 'C') ||
    setweight(to_tsvector('english', coalesce(NEW.requirements,'')), 'D');
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS jobs_search_vector_trigger ON jobs;
CREATE TRIGGER jobs_search_vector_trigger
  BEFORE INSERT OR UPDATE ON jobs
  FOR EACH ROW EXECUTE FUNCTION jobs_search_vector_update();

-- Backfill existing rows
UPDATE jobs SET title = title;

-- Password history (V9 created table, this adds index if missing)
CREATE INDEX IF NOT EXISTS idx_password_history_user_created
    ON password_history(user_id, created_at DESC);

-- Scheduler lock table (prevents duplicate runs in multi-instance deploy)
CREATE TABLE IF NOT EXISTS scheduler_lock (
    lock_name   VARCHAR(100) PRIMARY KEY,
    locked_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    locked_by   VARCHAR(100) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL
);
