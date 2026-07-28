-- V8 — single-use email-verification and password-reset tokens (§4.0.10, ADR 004).
--
-- One table for both purposes rather than two. They have identical shape and an identical
-- lifecycle — issue, expire, consume once — and two tables would mean two chances for one of them
-- to drift away from "hashed, expiring, single-use", which is the only property that matters here.
--
-- ONLY THE DIGEST IS STORED. The raw token exists in the mailed link and nowhere else, exactly as
-- refresh_token (V4) treats its own secret: a leaked database dump must not hand an attacker a
-- working password-reset link for every account in it.
--
-- SHA-256 rather than BCrypt, for the same reason V4 gives: the input is already 256 bits of
-- SecureRandom, so there is no dictionary to slow down, and the lookup has to be an indexed
-- equality match rather than a scan-and-compare across every outstanding token.

CREATE TABLE account_token (
    id           uuid        PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    -- EMAIL_VERIFICATION | PASSWORD_RESET. Constrained below so a typo in application code cannot
    -- create a third, silently unenforced kind of token.
    purpose      varchar(32) NOT NULL,
    -- SHA-256 hex, fixed width. `char` because the length is exact, so PostgreSQL's blank-padding
    -- never applies; the JPA entity declares the same JDBC type or `ddl-auto: validate` refuses
    -- to start.
    token_hash   char(64)    NOT NULL,
    expires_at   timestamptz NOT NULL,
    -- The single-use marker. Set exactly once, by a conditional UPDATE that also acts as the
    -- concurrency guard: two simultaneous clicks on the same link both attempt
    -- `SET consumed_at = now() WHERE consumed_at IS NULL`, and only one of them updates a row.
    consumed_at  timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_account_token_purpose
        CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET'))
);

-- The only read path, and the uniqueness that makes a collision impossible rather than unlikely.
CREATE UNIQUE INDEX ux_account_token_hash ON account_token (token_hash);

-- Covers "invalidate this account's outstanding tokens of this purpose", which runs whenever a
-- fresh link is issued. Without it a resend would leave the previous link live, so a mailbox with
-- three old messages in it would offer three working ways in.
CREATE INDEX ix_account_token_user_purpose ON account_token (user_id, purpose);

-- Supports the sweep that discards tokens which can no longer be redeemed, so the table stays
-- bounded rather than accumulating one row per reset request forever.
CREATE INDEX ix_account_token_expires_at ON account_token (expires_at);

COMMENT ON TABLE account_token IS
    '§4.0.10 — hashed, expiring, single-use verification and password-reset tokens.';
COMMENT ON COLUMN account_token.token_hash IS 'SHA-256 of the mailed token. The raw value is never stored.';
COMMENT ON COLUMN account_token.consumed_at IS 'Single-use marker; set by a conditional UPDATE that doubles as the race guard.';
