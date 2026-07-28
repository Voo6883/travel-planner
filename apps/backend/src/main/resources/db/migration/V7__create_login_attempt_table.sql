-- V7 — failed login attempts, for the bounded lockout in ADR 009 §6.
--
-- Database-backed on purpose. An in-memory counter would reset on every deploy and would count
-- separately on every instance, which breaks the locked stateless/multi-instance goal (ADR 009 §6)
-- and turns "5 failures / 15 min" into "5 failures per instance, until the next restart".
--
-- Only FAILURES are recorded. A successful login deletes the rows for its key, so this table is a
-- short-lived sliding window rather than an audit log — audit trails belong to task 12.
--
-- The key is the PAIR (login_identifier, client_ip), not the identifier alone. ADR 009 §6 requires
-- keying on both precisely because an identifier-only lock is a trivial targeted denial of service:
-- anyone who knows a username can lock its owner out by sending five wrong passwords.

CREATE TABLE login_attempt (
    id               uuid         PRIMARY KEY,
    -- Lower-cased as submitted. It is NOT a foreign key to "user": attempts against an address
    -- that was never registered must be counted too, or the lockout is itself an oracle telling an
    -- attacker which identifiers exist.
    login_identifier varchar(320) NOT NULL,
    -- 45 characters covers the longest IPv6 form, including an IPv4-mapped suffix.
    client_ip        varchar(45)  NOT NULL,
    attempted_at     timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_login_attempt_identifier_lowercase
        CHECK (login_identifier = lower(login_identifier))
);

-- The only read: "how many failures for this identifier from this address since <instant>".
CREATE INDEX ix_login_attempt_key
    ON login_attempt (login_identifier, client_ip, attempted_at);

-- Supports the sweep that discards rows older than the lockout window, so the table stays a
-- window rather than growing without bound.
CREATE INDEX ix_login_attempt_attempted_at ON login_attempt (attempted_at);

COMMENT ON TABLE login_attempt IS
    'ADR 009 §6 — sliding window of failed logins keyed on (identifier, client ip).';
