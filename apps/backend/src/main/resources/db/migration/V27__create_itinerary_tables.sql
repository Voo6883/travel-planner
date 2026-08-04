-- V27 — the deterministic itinerary aggregate (PLAN §4.1 "Timeline & legs", UC-C3-01/02/03/06/07/08,
-- BACKLOG S5-1, tasks/28).
--
-- This migration owns the *shape* of a plan. Feasibility is decided in the domain before anything
-- reaches here (`domain/algorithm/scheduling/`), and the constraints below are the second line:
-- they refuse the states the scheduler is written never to produce, so a bug in the scheduler
-- surfaces as a failed write rather than as a plan a traveller tries to walk.
--
-- `itinerary_leg` is deliberately absent. Task 29 owns routing, and a leg without a route lookup is
-- either empty or invented — the second is what PLAN §4.1.0 forbids. Items carry the anchors a leg
-- will join on (`itinerary_item.id`, ordinal within the day), so V29 adds legs without reshaping
-- anything here.

-- --------------------------------------------------------------------------------------------
-- itinerary — one live plan per trip.
--
-- The aggregate root. A trip has at most one itinerary at a time: UC-C3-04's "regenerate" replaces
-- the plan rather than accumulating versions, so a partial UNIQUE on trip_id is the invariant and
-- not a convention. History, when a task needs it, is a separate append-only table rather than a
-- second live row — two live plans is the state where "which one is mine" has no answer.
-- --------------------------------------------------------------------------------------------
CREATE TABLE itinerary (
    id                  uuid          PRIMARY KEY,

    trip_id             uuid          NOT NULL,
    user_id             uuid          NOT NULL,

    -- The destination this plan is for. Denormalised from trip.selected_recommendation_id's row
    -- because the plan outlives a re-selection: if a traveller changes their mind, the old plan is
    -- still readable as a plan for the old city while it is being replaced.
    destination_id      uuid          NOT NULL,

    -- DRAFT while being assembled; READY once every day validated. A plan is only ever published to
    -- the traveller as READY — see ck_itinerary_ready_has_days.
    status              varchar(32)   NOT NULL,

    -- Inclusive local dates, matching trip_brief's DateRange. Stored as dates rather than instants:
    -- a day on an itinerary is a calendar day in the destination's zone, and an instant would make
    -- "day 2" depend on where the reader is standing.
    start_date          date          NOT NULL,
    end_date            date          NOT NULL,

    -- IANA zone copied from destination at generation time. The plan must keep rendering the same
    -- clock times if the destination row is ever corrected, and every scheduled_start below is a
    -- local time in *this* zone.
    timezone            varchar(64)   NOT NULL,

    -- ItineraryScheduler.ALGORITHM_VERSION at the time of the run, so a plan built by an older
    -- scheduler is identifiable without re-deriving it.
    algorithm_version   varchar(64)   NOT NULL,

    version             integer       NOT NULL DEFAULT 0,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_itinerary_trip
        FOREIGN KEY (trip_id) REFERENCES trip (id) ON DELETE CASCADE,
    CONSTRAINT fk_itinerary_user
        FOREIGN KEY (user_id) REFERENCES "user" (id) ON DELETE CASCADE,
    CONSTRAINT fk_itinerary_destination
        FOREIGN KEY (destination_id) REFERENCES destination (id),

    CONSTRAINT ck_itinerary_status CHECK (status IN ('DRAFT', 'READY')),
    CONSTRAINT ck_itinerary_dates_ordered CHECK (end_date >= start_date),
    CONSTRAINT ck_itinerary_timezone_not_blank CHECK (length(btrim(timezone)) > 0),
    CONSTRAINT ck_itinerary_version_non_negative CHECK (version >= 0)
);

-- One live plan per trip (UC-C3-04 regenerate replaces rather than appends).
CREATE UNIQUE INDEX uq_itinerary_trip ON itinerary (trip_id);

-- Every read is "the plan for my trip", and the user_id predicate is what stops a plan being
-- readable across accounts, so both columns belong in the index rather than only trip_id.
CREATE INDEX ix_itinerary_user_trip ON itinerary (user_id, trip_id);

-- --------------------------------------------------------------------------------------------
-- itinerary_day — the day container (UC-C3-02, UC-C3-07).
-- --------------------------------------------------------------------------------------------
CREATE TABLE itinerary_day (
    id                  uuid          PRIMARY KEY,

    itinerary_id        uuid          NOT NULL,

    -- 1-based, and the traveller's own vocabulary: "day 2" is what they say and what the UI shows.
    day_number          integer       NOT NULL,

    -- The calendar date in the itinerary's timezone. Redundant against start_date + day_number by
    -- construction, and stored anyway: a day is queried and displayed by date far more often than
    -- by ordinal, and re-deriving it at every read is where an off-by-one eventually lands.
    day_date            date          NOT NULL,

    -- UC-C3-07: the area this day is clustered around, so the day minimises cross-city transit.
    -- Nullable because a day may legitimately span no single area — a travel day, or a plan for a
    -- destination whose areas were never curated. NULL means "not clustered", never "unknown area".
    area_id             uuid,

    -- Local wall-clock bounds the scheduler was given for this day. Persisted because they are an
    -- input to the plan, not a property of it: a day scheduled against 09:00-18:00 cannot be
    -- re-validated later against a different window without silently changing what "feasible" meant.
    window_start        time          NOT NULL,
    window_end          time          NOT NULL,

    CONSTRAINT fk_itinerary_day_itinerary
        FOREIGN KEY (itinerary_id) REFERENCES itinerary (id) ON DELETE CASCADE,
    CONSTRAINT fk_itinerary_day_area
        FOREIGN KEY (area_id) REFERENCES destination_area (id) ON DELETE SET NULL,

    CONSTRAINT ck_itinerary_day_number_positive CHECK (day_number >= 1),
    CONSTRAINT ck_itinerary_day_window_ordered CHECK (window_end > window_start)
);

CREATE UNIQUE INDEX uq_itinerary_day_number ON itinerary_day (itinerary_id, day_number);
CREATE UNIQUE INDEX uq_itinerary_day_date ON itinerary_day (itinerary_id, day_date);

-- --------------------------------------------------------------------------------------------
-- itinerary_item — one scheduled block (UC-C3-03, UC-C3-06, UC-C3-08).
--
-- <b>Times are `time`, not `timestamptz`.</b> A scheduled block is a wall-clock intent — "the
-- temple at 09:30" — in the itinerary's zone. Storing an instant would fix it against a UTC offset
-- and move the whole plan by an hour the next time that zone's DST rules change, which is precisely
-- the class of bug the timezone column exists to avoid.
-- --------------------------------------------------------------------------------------------
CREATE TABLE itinerary_item (
    id                  uuid          PRIMARY KEY,

    itinerary_day_id    uuid          NOT NULL,

    -- Position within the day, 0-based and gapless. The ordering key legs will join on in task 29:
    -- scheduled_start would collapse two items that start at the same minute, which the constraints
    -- below forbid for real items but not for zero-duration ones a later task may add.
    ordinal             integer       NOT NULL,

    -- UC-C3-03: the POI this block is grounded in. Nullable for FREE_TIME and for a meal slot the
    -- scheduler reserved but could not fill from the KB — a held slot with no POI is honest, while
    -- naming a restaurant nobody curated is the invented fact PLAN §4.1.0 forbids.
    poi_id              uuid,

    category            varchar(32)   NOT NULL,

    -- Denormalised display title. A plan has to remain readable after a POI is recurated or
    -- withdrawn, and a timeline that renders "(deleted)" for last month's plan is worse than one
    -- that keeps the name it was built with.
    title               varchar(200)  NOT NULL,

    scheduled_start     time          NOT NULL,
    scheduled_end       time          NOT NULL,

    -- Stored rather than derived from the two times above so that the intended visit length
    -- survives a later edit that moves the block: "90 minutes at the temple" is the fact, and the
    -- clock positions are where the scheduler happened to put it.
    duration_minutes    integer       NOT NULL,

    -- UC-C3-03. The citation for the fact this block asserts, carried from the POI's provenance so
    -- the timeline can show a source without re-reading the knowledge base.
    source_ref          varchar(200),

    notes               text,

    CONSTRAINT fk_itinerary_item_day
        FOREIGN KEY (itinerary_day_id) REFERENCES itinerary_day (id) ON DELETE CASCADE,
    -- No ON DELETE CASCADE: withdrawing a POI must not silently delete somebody's plan for it.
    -- SET NULL keeps the block, its title and its times; the citation goes stale, which is a fact
    -- the traveller can see rather than a hole in their day.
    CONSTRAINT fk_itinerary_item_poi
        FOREIGN KEY (poi_id) REFERENCES poi (id) ON DELETE SET NULL,

    CONSTRAINT ck_itinerary_item_category CHECK (
        category IN ('SIGHT', 'FOOD', 'TRANSIT', 'FREE_TIME')),
    CONSTRAINT ck_itinerary_item_ordinal_non_negative CHECK (ordinal >= 0),
    CONSTRAINT ck_itinerary_item_times_ordered CHECK (scheduled_end > scheduled_start),
    CONSTRAINT ck_itinerary_item_duration_positive CHECK (duration_minutes >= 1),
    CONSTRAINT ck_itinerary_item_title_not_blank CHECK (length(btrim(title)) > 0)
);

CREATE UNIQUE INDEX uq_itinerary_item_ordinal ON itinerary_item (itinerary_day_id, ordinal);

-- The timeline read: every item of a day, already in clock order (UC-C3-08).
CREATE INDEX ix_itinerary_item_day_start ON itinerary_item (itinerary_day_id, scheduled_start);

-- Two blocks in one day may not start at the same minute. This is the cheap half of the no-overlap
-- rule; the full interval check is `DayScheduleValidator`, because a CHECK cannot see sibling rows
-- and an EXCLUDE constraint over `time` ranges would reject the legitimate case where one block
-- ends exactly when the next begins.
CREATE UNIQUE INDEX uq_itinerary_item_start ON itinerary_item (itinerary_day_id, scheduled_start);
