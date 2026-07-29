-- V20 — the rest of the TripBrief (tasks/18-trip-brief-core.md, PLAN §8, §4.1.3).
--
-- V6 created `trip_brief` as a foundation and said so in its own header: the one-to-one link to
-- `trip`, the ADR 008 version, and the two value objects whose mapping everything else reuses
-- (Money, DateRange). It deferred the rest of the shape to this task, so this migration is not a
-- schema change to a finished table — it is the second half of one that was always going to be
-- written here.
--
-- Nothing is backfilled and nothing is NOT NULL that could not be. A brief under construction is
-- incomplete by definition (PLAN §4.1.3 answers incompleteness with typed clarification, not with
-- a rejected save), so every scalar column added here is nullable and the two collections default
-- to empty. `ClarificationNeeded` decides what "complete" means; the schema deliberately does not,
-- because a NOT NULL here would make an in-progress brief unsavable.

ALTER TABLE trip_brief
    -- text[] rather than a join table, following `poi.tags` (V15): destination preferences are a
    -- short ordered list read only alongside the rest of the brief, never queried on their own. A
    -- child table would add a join to every brief read for a query that does not exist, and an
    -- ordered child table would additionally need a position column to preserve "most preferred
    -- first" — which the array gives for free.
    --
    -- The values are `destination.slug`, not `destination.id`. The slug is the public handle
    -- everything else already speaks (ADR 010 §4, `destination_not_covered.details.supported`), and
    -- a foreign key would be wrong in any case: coverage is checked by the application against
    -- KnowledgePort so an uncovered slug can be refused with the supported list attached, whereas a
    -- constraint violation carries nothing a user could act on.
    ADD COLUMN destinations     text[]       NOT NULL DEFAULT '{}',
    ADD COLUMN date_flexibility varchar(32),
    -- Free text, and deliberately not a slug. People depart from places the knowledge base has
    -- never curated, and requiring coverage on the origin would refuse a perfectly valid trip.
    ADD COLUMN departure_city   varchar(120),
    -- Two counts, not one total. Room occupancy, entry pricing, and the `area_coverage` term of the
    -- C2 fit score all treat a family of four differently from four adults; a single `travellers`
    -- column would force every consumer to guess which it meant.
    ADD COLUMN party_adults     integer,
    ADD COLUMN party_children   integer,
    ADD COLUMN interests        text[]       NOT NULL DEFAULT '{}',
    ADD COLUMN pace             varchar(16);

-- Half a PartySize is not a PartySize — the same rule V6 applied to the Money and DateRange pairs.
ALTER TABLE trip_brief
    ADD CONSTRAINT ck_trip_brief_party_paired
        CHECK ((party_adults IS NULL) = (party_children IS NULL)),
    -- Mirrors the PartySize invariants so a direct SQL write cannot create a party the domain
    -- constructor would have rejected: at least one adult, no negative children, and a total no
    -- larger than a single party any C4 supplier will quote for.
    ADD CONSTRAINT ck_trip_brief_party_bounds
        CHECK (party_adults IS NULL
               OR (party_adults >= 1 AND party_children >= 0 AND party_adults + party_children <= 20)),
    -- Enumerated in a CHECK rather than a Postgres ENUM type, for the reason V5 gives for
    -- `trip.status`: adding a value to a native enum needs DDL that cannot run in every migration
    -- context. The values are owned by the domain enums com.travelplanner.domain.enums.
    -- DateFlexibility and TravelPace, and a test asserts each list against its enum.
    ADD CONSTRAINT ck_trip_brief_date_flexibility
        CHECK (date_flexibility IS NULL OR date_flexibility IN (
            'FIXED',
            'FLEXIBLE_WEEK',
            'FLEXIBLE_MONTH'
        )),
    ADD CONSTRAINT ck_trip_brief_pace
        CHECK (pace IS NULL OR pace IN (
            'RELAXED',
            'MODERATE',
            'PACKED'
        )),
    -- An array column cannot be constrained by an enumerated CHECK the way a scalar can without
    -- repeating the vocabulary in a subquery, so the bound that IS worth enforcing is the size:
    -- a preference list longer than this was not ranked by anybody, and an unbounded array on a
    -- user-writable row is a storage amplification the request body should never have allowed.
    ADD CONSTRAINT ck_trip_brief_destinations_bounded
        CHECK (array_length(destinations, 1) IS NULL OR array_length(destinations, 1) <= 10),
    ADD CONSTRAINT ck_trip_brief_interests_bounded
        CHECK (array_length(interests, 1) IS NULL OR array_length(interests, 1) <= 16);
