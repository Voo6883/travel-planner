-- V26 — research-complete mail idempotency (tasks/27, UC-C2-08, UC-N04, BACKLOG S4-8).
--
-- WHY THIS EXISTS
--
-- Research completion is asynchronous: the worker marks the job COMPLETED and the trip
-- RESEARCH_READY inside one transaction, then mail may be dispatched after commit. A crash
-- between those steps, a reconciler retry, or a duplicate AFTER_COMMIT listener must not send
-- the same research-complete message twice (task 27 Do-not: "Do not send duplicate completion
-- mail"). Storing the send stamp on the job itself makes the claim atomic with the job row
-- under the existing ADR 008 version lock — a second notifier that loses the race sees a
-- non-null stamp and becomes a no-op.
--
-- A separate outbox table was considered and rejected: v1 has one notification type per job and
-- no retry worker. An outbox without a worker is just a second place to lose the stamp.

ALTER TABLE research_job
    ADD COLUMN completion_mail_sent_at timestamptz;

-- A stamp is meaningful only on a completed run. Active or failed jobs must not claim a send.
ALTER TABLE research_job
    ADD CONSTRAINT ck_research_job_completion_mail_only_when_completed
        CHECK (completion_mail_sent_at IS NULL OR status = 'COMPLETED');

COMMENT ON COLUMN research_job.completion_mail_sent_at IS
    'tasks/27 — when the research-complete mail was claimed for this job; NULL until claimed.';
