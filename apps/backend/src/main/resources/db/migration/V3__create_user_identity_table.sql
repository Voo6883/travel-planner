-- V3 — provider identities linked to an account (PLAN §8).
--
-- One row per (provider, subject) pair. A single account may hold LOCAL plus FIREBASE_GOOGLE plus
-- GITHUB rows; ADR 009 §4 governs when linking is allowed to happen automatically. That policy is
-- task 10's; the storage shape it needs is here.

CREATE TABLE user_identity (
    id                   uuid         PRIMARY KEY,
    user_id              uuid         NOT NULL,
    provider             varchar(32)  NOT NULL,
    -- The provider's own immutable subject identifier (Firebase uid, GitHub numeric id, or the
    -- user id for LOCAL). Never the email: emails change hands, subjects do not.
    provider_subject_id  varchar(255) NOT NULL,
    email                varchar(320),
    created_at           timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_user_identity_user FOREIGN KEY (user_id) REFERENCES "user" (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_identity_provider CHECK (provider IN ('LOCAL', 'FIREBASE_GOOGLE', 'GITHUB')),
    -- PLAN §8: the same provider subject can never resolve to two accounts. This is what stops a
    -- second sign-in from silently forking a duplicate account.
    CONSTRAINT ux_user_identity_provider_subject UNIQUE (provider, provider_subject_id)
);

-- Supports "list my linked providers" (UC-A11) and the unlink check in ADR 009 §4, both of which
-- read every identity for one user.
CREATE INDEX ix_user_identity_user_id ON user_identity (user_id);
