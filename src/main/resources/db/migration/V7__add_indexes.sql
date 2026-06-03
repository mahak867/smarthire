-- ── SmartHire · V7__add_indexes.sql ──
-- Applications
CREATE INDEX ix_app_job_id        ON applications(job_id);
CREATE INDEX ix_app_candidate_id  ON applications(candidate_id);
CREATE INDEX ix_app_status        ON applications(status);
CREATE INDEX ix_app_ai_score_desc ON applications(ai_score DESC NULLS LAST);
CREATE INDEX ix_app_job_status    ON applications(job_id, status);
CREATE INDEX ix_app_scoring       ON applications(scoring_complete) WHERE scoring_complete = FALSE;

-- Jobs
CREATE INDEX ix_jobs_status          ON jobs(status);
CREATE INDEX ix_jobs_posted_by       ON jobs(posted_by);
CREATE INDEX ix_jobs_status_created  ON jobs(status, created_at DESC);
CREATE INDEX ix_jobs_fts             ON jobs USING gin(to_tsvector('english', title || ' ' || description));

-- Interviews
CREATE INDEX ix_interviews_app       ON interviews(application_id);
CREATE INDEX ix_interviews_scheduled ON interviews(scheduled_at);
CREATE INDEX ix_interviews_status    ON interviews(status);

-- Audit logs
CREATE INDEX ix_audit_user_created   ON audit_logs(user_id, created_at DESC);
CREATE INDEX ix_audit_entity         ON audit_logs(entity_type, entity_id);
CREATE INDEX ix_audit_action         ON audit_logs(action, created_at DESC);
