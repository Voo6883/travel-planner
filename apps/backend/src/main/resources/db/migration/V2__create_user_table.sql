-- V2 — the user account (PLAN §8; ADR 009 §1 and §4).
--
-- The table name is the SQL reserved word `user`, so every reference must stay double-quoted.
-- PLAN §8 names it `user`; renaming it to `app_user` would be an undocumented schema decision.
--
-- Behaviour (password hashing, lockout, verification) belongs to tasks 08 and 09. This migration
-- creates only the columns those tasks need to exist before they can be written, plus the two
-- revocation columns ADR 009 requires — they are free to add now and painful to retrofit once
-- three identity adapters have been built against a stateless assumption.

CREATE TABLE "user" (
    id                    uuid        PRIMARY KEY,
    -- Nullable: an account created through Google or GitHub has no username until the user picks
    -- one. Uniqueness is case-insensitive and enforced by an index below, not by this column.
    username              varchar(64),
    email                 varchar(320) NOT NULL,
    -- Nullable by design. `password_hash IS NULL` is the marker for an OAuth-only account, which
    -- ADR 009 §4 uses to refuse minting a local password through the forgot-password flow.
    password_hash         varchar(100),
    email_verified        boolean     NOT NULL DEFAULT false,
    role                  varchar(16) NOT NULL DEFAULT 'USER',
    enabled               boolean     NOT NULL DEFAULT true,
    -- ADR 009 §1: bumped to revoke every live session for this account. The JWT carries `tv`;
    -- the filter rejects a token whose `tv` no longer matches.
    token_version         integer     NOT NULL DEFAULT 0,
    -- ADR 009 §1: rejects tokens whose `iat` predates this instant.
    sessions_valid_after  timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_user_role CHECK (role IN ('USER', 'ADMIN')),
    -- ADR 009 §4: a username may not contain '@'. Without this, a username can impersonate
    -- somebody else's email address on the "email or username" login path.
    CONSTRAINT ck_user_username_not_email CHECK (username IS NULL OR position('@' IN username) = 0),
    CONSTRAINT ck_user_token_version_non_negative CHECK (token_version >= 0)
);

-- Case-insensitive uniqueness (ADR 009 §4). A plain UNIQUE column would let `Alice@x.com` and
-- `alice@x.com` both register and then race for the same identity at login.
CREATE UNIQUE INDEX ux_user_email_lower ON "user" (lower(email));
CREATE UNIQUE INDEX ux_user_username_lower ON "user" (lower(username)) WHERE username IS NOT NULL;

COMMENT ON TABLE "user" IS 'Account root. One user owns many trips; there is no tenant concept.';
COMMENT ON COLUMN "user".token_version IS 'ADR 009 §1 — bump to revoke all sessions.';
COMMENT ON COLUMN "user".sessions_valid_after IS 'ADR 009 §1 — reject tokens issued before this instant.';
