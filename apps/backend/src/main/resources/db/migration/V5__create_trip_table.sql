-- V5 — the trip aggregate root (PLAN §8, USE-CASES "Trip status").
--
-- `trip` is the first table in the documented lock order (see
-- infrastructure/persistence/package-info.java). Everything user-scoped hangs off it.

CREATE TABLE trip (
    id                          uuid         PRIMARY KEY,
    user_id                     uuid         NOT NULL,
    name                        varchar(120) NOT NULL,
    status                      varchar(32)  NOT NULL,
    -- Deliberately NOT a foreign key. `ranked_recommendation` is created by tasks/21; adding the
    -- constraint now would mean inventing that table early, which the brief forbids. Task 21 adds
    -- the FK in its own migration.
    selected_recommendation_id  uuid,
    -- ADR 008 §1. The agent and the debounced brief form both write this aggregate; without a
    -- version the second write silently discards the first.
    version                     integer      NOT NULL DEFAULT 0,
    created_at                  timestamptz  NOT NULL DEFAULT now(),
    updated_at                  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_trip_user FOREIGN KEY (user_id) REFERENCES "user" (id),
    -- The full wizard-progress vocabulary from plans/USE-CASES.md, including ARCHIVED. Enumerated
    -- in a CHECK rather than a Postgres ENUM type: adding a value to a native enum needs DDL that
    -- cannot run inside every migration context, and the values are owned by the domain enum
    -- com.travelplanner.domain.enums.TripStatus, which a test asserts against this list.
    CONSTRAINT ck_trip_status CHECK (status IN (
        'DRAFT',
        'BRIEF_COMPLETE',
        'CLARIFICATION_NEEDED',
        'RESEARCH_QUEUED',
        'RESEARCH_RUNNING',
        'RESEARCH_READY',
        'DESTINATION_SELECTED',
        'ITINERARY_READY',
        'BOOKING_IN_PROGRESS',
        'BOOKED',
        'ARCHIVED'
    )),
    CONSTRAINT ck_trip_version_non_negative CHECK (version >= 0)
);

-- Every read is scoped by user_id (PLAN §4.0.2-L). The trip list is ordered newest first, so the
-- index carries created_at to keep that query index-only on the ordering column.
CREATE INDEX ix_trip_user_id_created_at ON trip (user_id, created_at DESC);
