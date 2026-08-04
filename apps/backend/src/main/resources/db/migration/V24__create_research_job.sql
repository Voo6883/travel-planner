-- V24 — durable research-job orchestration (PLAN C2 §3.1, USE-CASES UC-C2-01/02, tasks/23).
--
-- A research run is a long-running agent (up to the §14 90s budget) that MUST NOT block an HTTP
-- request thread. `research_job` is the durable record of one such run: the client starts it
-- (202 + job_id), the work happens on a background worker, and the client polls this row. Because
-- the worker and the reconciler act with no user session, the job carries enough identity to be
-- operated on entirely from its own row.

CREATE TABLE research_job (
    id                uuid         PRIMARY KEY,

    -- The trip this run researches. ON DELETE CASCADE: a deleted trip has no research to keep.
    trip_id           uuid         NOT NULL,

    -- The owner, denormalised from `trip`. The trip still holds the canonical ownership; this copy
    -- exists because the background worker and the startup reconciler have no UserContext and must
    -- still reach the trip through the user-scoped port (PLAN §4.0.2-L). Without it, recovering a
    -- crashed job's trip would need an unscoped trip lookup, which TripRepositoryPort forbids by
    -- design. ON DELETE CASCADE for the same reason as trip_id.
    user_id           uuid         NOT NULL,

    -- The identity of this particular run, handed to task 25 so recommendations it persists can be
    -- attributed to the run that produced them. Equal to `id` at creation (one row, one run); kept
    -- as its own column so a future model where a job spans several runs does not need a migration.
    research_run_id   uuid         NOT NULL,

    -- Mirrors domain/enums/ResearchJobStatus, UPPER_SNAKE like ck_trip_status (V5) and
    -- ck_message_status (V19); MigrationContractTest asserts the two lists are identical. The wire
    -- form is lower-case (`queued|running|completed|failed`), mapped at the DTO boundary only.
    status            varchar(16)  NOT NULL,

    -- Coarse progress for the poll UI. Never orders anything and is advisory only.
    progress_pct      integer      NOT NULL DEFAULT 0,

    -- A registered-style snake_case identifier when the run failed, e.g. `research_timeout`. NULL on
    -- every non-failed row (see ck_research_job_error_code_only_when_failed).
    error_code        varchar(64),

    -- How many times the run has entered RUNNING. Incremented on each start; a re-run creates a NEW
    -- row rather than advancing this, so history is preserved (UC-C2-07).
    attempts          integer      NOT NULL DEFAULT 0,

    -- Stamped when the run enters RUNNING; NULL while QUEUED (ck_research_job_started_at_matches).
    started_at        timestamptz,
    -- Stamped when the run reaches a terminal state; NULL while active
    -- (ck_research_job_completed_at_matches_terminal).
    completed_at      timestamptz,

    -- ADR 008 §1 optimistic lock: the background worker and the reconciler can both touch a job, and
    -- a lost update would revive a job the other just finished.
    version           integer      NOT NULL DEFAULT 0,

    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_research_job_trip FOREIGN KEY (trip_id) REFERENCES trip (id) ON DELETE CASCADE,
    CONSTRAINT fk_research_job_user FOREIGN KEY (user_id) REFERENCES "user" (id) ON DELETE CASCADE,

    CONSTRAINT ck_research_job_status CHECK (status IN (
        'QUEUED',
        'RUNNING',
        'COMPLETED',
        'FAILED'
    )),
    CONSTRAINT ck_research_job_progress_pct_range CHECK (progress_pct BETWEEN 0 AND 100),
    CONSTRAINT ck_research_job_version_non_negative CHECK (version >= 0),

    -- An error code is exactly the failure marker: present on a FAILED row, absent on every other.
    -- Written as an implication rather than an equivalence because a FAILED row must carry one and a
    -- non-FAILED row must not.
    CONSTRAINT ck_research_job_error_code_only_when_failed
        CHECK (error_code IS NULL OR status = 'FAILED'),
    -- started_at records the RUNNING transition, so it is set exactly once the run has begun and
    -- stays set through the terminal states a RUNNING run reaches.
    CONSTRAINT ck_research_job_started_at_matches
        CHECK ((started_at IS NOT NULL) = (status IN ('RUNNING', 'COMPLETED', 'FAILED'))),
    -- completed_at records the terminal transition: present exactly when the run has finished.
    CONSTRAINT ck_research_job_completed_at_matches_terminal
        CHECK ((completed_at IS NOT NULL) = (status IN ('COMPLETED', 'FAILED')))
);

-- The poll/history read: a trip's jobs, newest first (UC-C2-07 keeps previous runs as history).
CREATE INDEX ix_research_job_trip_created ON research_job (trip_id, created_at DESC);

-- At most one ACTIVE (queued or running) job per trip. Partial, so completed and failed rows
-- accumulate freely as history while a second concurrent start is refused at the database rather
-- than only in the service — the check-then-insert in ResearchJobService is racy on its own, and
-- this index is what makes "no duplicate active job" a guarantee instead of an intention.
CREATE UNIQUE INDEX uq_research_job_active_per_trip ON research_job (trip_id)
    WHERE status IN ('QUEUED', 'RUNNING');
