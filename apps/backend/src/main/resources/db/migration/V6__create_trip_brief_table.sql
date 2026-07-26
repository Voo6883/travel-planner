-- V6 — trip brief foundation (PLAN §8, ADR 008 §1).
--
-- SCOPE NOTE. This is the foundation, not the finished brief. tasks/18-trip-brief-core.md owns
-- the full TripBrief shape (destination preferences, date flexibility, departure, party, interests,
-- pace) and adds those columns in its own migration. Creating them here would be exactly the
-- "complete schema early" the task 07 brief forbids.
--
-- What is here is what the foundation must prove: a one-to-one child of `trip` with audit columns,
-- an ADR 008 version, a `numeric` money pair, and a date range — the two value objects
-- (Money, DateRange) whose persistence mapping every later feature reuses.

CREATE TABLE trip_brief (
    id               uuid        PRIMARY KEY,
    -- UNIQUE, not just a FK: a trip has at most one brief. Enforcing that in the schema removes
    -- the "which brief is current?" question from every later query.
    trip_id          uuid        NOT NULL,
    -- Money is numeric + a currency code, never float (PLAN §4.0.2-A). scale 4 leaves room for
    -- currencies with more than two minor units and for pre-rounding intermediate totals.
    budget_amount    numeric(19, 4),
    budget_currency  varchar(3),
    start_date       date,
    end_date         date,
    -- ADR 008 §1.
    version          integer     NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_trip_brief_trip FOREIGN KEY (trip_id) REFERENCES trip (id) ON DELETE CASCADE,
    CONSTRAINT ux_trip_brief_trip_id UNIQUE (trip_id),
    -- Half a Money is not a Money. Either both columns are present or neither is.
    CONSTRAINT ck_trip_brief_budget_paired
        CHECK ((budget_amount IS NULL) = (budget_currency IS NULL)),
    CONSTRAINT ck_trip_brief_budget_non_negative
        CHECK (budget_amount IS NULL OR budget_amount >= 0),
    -- Mirrors the DateRange invariant so a direct SQL write cannot create a range the domain
    -- constructor would have rejected.
    CONSTRAINT ck_trip_brief_dates_paired
        CHECK ((start_date IS NULL) = (end_date IS NULL)),
    CONSTRAINT ck_trip_brief_date_order
        CHECK (start_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_trip_brief_version_non_negative CHECK (version >= 0)
);
