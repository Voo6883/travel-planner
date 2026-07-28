-- V9 — the per-email and per-address rate limit ADR 009 §6 requires for
-- POST /auth/password/forgot and POST /auth/verify-email/resend.
--
-- Separate from login_attempt (V7) on purpose. That table is a lockout window over *failed
-- credentials*, cleared by a successful sign-in; this one counts *successful* requests for a mail,
-- which have no notion of success or failure to clear them. Folding the two together would mean a
-- forgot-password request could contribute to a sign-in lockout, or a login could reset a mail
-- quota — either of which is a security control accidentally disabling another.
--
-- Database-backed for the same reason V7 is: an in-process counter resets on deploy and counts
-- separately per instance, which breaks the locked stateless/multi-instance goal.
--
-- NO RAW ADDRESS IS STORED. The subject is a SHA-256 digest of the lower-cased email, or of the
-- client IP. §4.0.10 requires that mail auditing keep recipient PII out of logs; storing the
-- plaintext address in a quota table would put back exactly what the logging rule removes, and the
-- counter only ever needs equality, which a digest preserves.

CREATE TABLE mail_rate_limit (
    id           uuid        PRIMARY KEY,
    -- e.g. 'password_forgot:email', 'verify_email_resend:ip'. Free-form rather than a CHECK
    -- constraint: task 12's admin mails and later notification types will add scopes, and a
    -- constraint here would turn each of those into a migration.
    scope        varchar(64) NOT NULL,
    -- SHA-256 hex of the lower-cased email address, or of the client IP.
    subject_hash char(64)    NOT NULL,
    requested_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_mail_rate_limit_scope_lowercase CHECK (scope = lower(scope))
);

-- The only read: "how many requests in this scope for this subject since <instant>".
CREATE INDEX ix_mail_rate_limit_key ON mail_rate_limit (scope, subject_hash, requested_at);

-- Supports the sweep that keeps the table a sliding window rather than an unbounded log.
CREATE INDEX ix_mail_rate_limit_requested_at ON mail_rate_limit (requested_at);

COMMENT ON TABLE mail_rate_limit IS
    'ADR 009 §6 — sliding window of mail requests, keyed on hashed email and hashed client IP.';
COMMENT ON COLUMN mail_rate_limit.subject_hash IS 'SHA-256 of the email or IP. Never the plaintext.';
