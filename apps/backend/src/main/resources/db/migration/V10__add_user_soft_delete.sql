-- V10 — soft delete for UC-A14 (`DELETE /auth/me`).
--
-- WHY THE ROW SURVIVES. A hard delete has only two endings, and both are wrong: cascade, and the
-- user's trips, itineraries, and future booking records disappear with them; or restrict, and the
-- delete fails for anyone who ever used the product. PLAN §8 makes `user` the owner of every
-- aggregate, so the id has to keep existing for referential integrity to keep meaning.
--
-- WHAT IS ERASED. Everything that identifies a person. The application replaces the email with an
-- unroutable placeholder at `@deleted.invalid` (RFC 2606 reserves `.invalid`, so it can never be
-- delivered or re-registered by accident), releases the username so somebody else may take it, and
-- drops the password hash so no credential remains. That is the anonymisation UC-A14 asks for, and
-- it is done in application code rather than here because it runs per account, not per deployment.
--
-- WHAT STOPS THE SESSION. `enabled = false` is what JwtAuthenticationFilter reads on every request,
-- and the deletion path also bumps `token_version` through SessionRevocationService (ADR 009 §1).
-- `deleted_at` is the audit fact — "this account was closed, and when" — which `enabled` alone
-- cannot express, because an administrator disabling an account (§4.0.6) sets the same flag.

ALTER TABLE "user"
    ADD COLUMN deleted_at timestamptz;

-- Partial index: the only query is "the live accounts", and indexing the deleted ones would grow
-- the index for rows no lookup ever wants.
CREATE INDEX ix_user_deleted_at ON "user" (deleted_at) WHERE deleted_at IS NOT NULL;

-- A closed account must never be reachable through a credential. The anonymiser drops the hash;
-- this makes it a schema rule so that no later code path can put one back.
ALTER TABLE "user"
    ADD CONSTRAINT ck_user_deleted_has_no_password
        CHECK (deleted_at IS NULL OR password_hash IS NULL);

COMMENT ON COLUMN "user".deleted_at IS
    'UC-A14 — soft delete. The row is retained for referential integrity; the PII columns are anonymised.';
