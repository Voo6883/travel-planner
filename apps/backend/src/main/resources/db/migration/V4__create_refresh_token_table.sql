-- V4 — refresh token storage (ADR 009 §3).
--
-- Schema only. The rotation, reuse-detection, and revocation logic is task 08's; the columns are
-- created here because ADR 009 makes refresh and revocation v1 features and every auth path is
-- written against the assumption that they exist.
--
-- Tokens are stored HASHED. A leaked database dump must not hand out live 14-day sessions, so the
-- raw token exists only in the httpOnly cookie and is never persisted.

CREATE TABLE refresh_token (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL,
    -- SHA-256 hex digest: 64 characters, fixed width. Unique so that reuse detection is a
    -- lookup rather than a scan, and so two families can never collide.
    token_hash  char(64)    NOT NULL,
    expires_at  timestamptz NOT NULL,
    -- Set when this token was exchanged for a successor. A rotated token presented again is the
    -- reuse signal that revokes the whole family (ADR 009 §3).
    rotated_at  timestamptz,
    revoked_at  timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES "user" (id) ON DELETE CASCADE,
    CONSTRAINT ux_refresh_token_hash UNIQUE (token_hash)
);

-- "Revoke every session for this user" and "expire old rows" are the only two access patterns.
CREATE INDEX ix_refresh_token_user_id ON refresh_token (user_id);
CREATE INDEX ix_refresh_token_expires_at ON refresh_token (expires_at);
