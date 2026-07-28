-- V12 — audit_event (PLAN §4.0.6 "every admin action writes to audit_event (who, what, target
-- user, when)"; §13.2 checklist "Admin audit — password reset / user changes logged"; backlog S2-2).
--
-- ============================================================================================
-- WHAT THIS TABLE DELIBERATELY DOES NOT CONTAIN
-- ============================================================================================
-- No password. No password hash. No email address. No free-text note.
--
-- RESET_PASSWORD exists to set a new password, so a column able to hold one would eventually hold
-- one — in the single table with the longest retention and the widest read audience in the system.
-- The guarantee is structural rather than procedural: AdminAuditEvent has no field for it, so
-- adding one would mean changing the record, the entity, the adapter, and this file.
--
-- The two user ids stay. They are internal surrogate keys, not personal data, and without them the
-- trail cannot answer either half of "who did this, and to whom" — which is the only question it
-- exists to answer.
--
-- ============================================================================================
-- WHY THERE IS NO FOREIGN KEY
-- ============================================================================================
-- Same reasoning as ai_call_log (V11). This is an append-only record that must outlive the rows it
-- describes: a foreign key would either block an account lifecycle operation or cascade away the
-- evidence of what an administrator did to that account. UC-A14 soft-deletes rather than deleting,
-- so the ids remain resolvable in practice; the absence of the constraint is what keeps that a
-- convenience rather than a dependency.
--
-- ============================================================================================
-- WHY THE ROW SHARES ITS CALLER'S TRANSACTION
-- ============================================================================================
-- Unlike ai_call_log, whose adapter runs REQUIRES_NEW so a metrics row survives the failure it
-- documents, AdminAuditPort joins the caller's transaction. An audit row that outlived a
-- rolled-back mutation would assert that an administrator reset somebody's password when they did
-- not. A trail that can lie is worse than no trail, so a refused or failed action leaves no row —
-- refusals are security-log events instead.

CREATE TABLE audit_event (
    id              uuid        PRIMARY KEY,

    -- Who performed it. Always an account with role = 'ADMIN' at the time of the action; the role
    -- is not copied here because it is the authorisation check that mattered, not a fact to store.
    actor_user_id   uuid        NOT NULL,
    -- Whose account was changed. Admin actions in v1 always target exactly one account (§4.0.6).
    target_user_id  uuid        NOT NULL,

    action          varchar(32) NOT NULL,
    result          varchar(16) NOT NULL,

    -- The X-Request-Id from the MDC (PLAN §4.0.2-J2), which is what joins this row to the log lines
    -- the same request produced. Nullable: a future scheduled or console-initiated action has no
    -- HTTP request behind it.
    request_id      varchar(64),

    -- "when". No updated_at: the table is append-only and a row is never revised. An audit record
    -- that could be edited would not be one.
    created_at      timestamptz NOT NULL DEFAULT now(),

    -- Mirrors domain/enums/AdminAction. MigrationContractTest asserts the two lists are identical,
    -- so adding a capability without widening this constraint fails the build rather than the
    -- insert.
    CONSTRAINT ck_audit_event_action
        CHECK (action IN ('DISABLE_USER', 'ENABLE_USER', 'RESET_PASSWORD')),
    -- Mirrors domain/enums/AdminActionResult. PARTIAL means the account change committed but its
    -- ADR 009 §1 session revocation found no account to revoke — a disabled account that may still
    -- hold a live token, which is exactly what an operator needs to be able to find afterwards.
    CONSTRAINT ck_audit_event_result
        CHECK (result IN ('SUCCESS', 'PARTIAL'))
);

-- "What has been done to this account, most recent first" — the query behind the user detail
-- screen's history and behind every "why is my account disabled?" support request.
CREATE INDEX ix_audit_event_target_created ON audit_event (target_user_id, created_at DESC);

-- "What has this administrator done" — the review query, and the one an investigation starts from
-- when the suspect account is the actor rather than the target.
CREATE INDEX ix_audit_event_actor_created ON audit_event (actor_user_id, created_at DESC);

COMMENT ON TABLE audit_event IS
    'PLAN §4.0.6 — administrative account mutations. Append-only. Contains NO password, hash, or PII.';
COMMENT ON COLUMN audit_event.result IS
    'SUCCESS, or PARTIAL when the change committed but its ADR 009 session revocation found nothing.';
