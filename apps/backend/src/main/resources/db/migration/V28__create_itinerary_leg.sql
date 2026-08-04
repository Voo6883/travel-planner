-- V28 — the transport between two scheduled blocks (UC-C3-09/10/11, UC-K10, BACKLOG S5-2 mobility,
-- tasks/29).
--
-- Task 28 left this table out deliberately: a leg without a route lookup is either empty or
-- invented, and PLAN §4.1.0 forbids the second. The anchors it joins on were already in place —
-- `itinerary_item.id` and a gapless `ordinal` in clock order — so this adds legs without reshaping
-- V27.
--
-- <b>A leg is an instance, not a template.</b> `route_segment` in the knowledge base is the reusable
-- fact ("Shibuya to Asakusa is about 35 minutes by metro"); a row here is that fact applied to one
-- traveller's Tuesday afternoon. The FK back to the segment is what lets a reader see the citation
-- behind the number, and it is nullable because the honest fallbacks below have no segment at all.

CREATE TABLE itinerary_leg (
    id                  uuid          PRIMARY KEY,

    -- Denormalised from the two items' shared day. Every read is "the legs of this day", and
    -- reaching it through two item joins would make the timeline query pay for the normalisation.
    itinerary_day_id    uuid          NOT NULL,

    from_item_id        uuid          NOT NULL,
    to_item_id          uuid          NOT NULL,

    -- UC-C3-10. Exactly `TransportKind`, which is the curated vocabulary and has no "unknown"
    -- member -- a curated mode is always a real mode. So an UNKNOWN leg carries NULL here rather
    -- than a synthetic value: the absence is the honest statement, and inventing an enum constant
    -- to hold it would put "we do not know" into the same column the UI switches an icon on.
    transport_mode      varchar(32),

    -- How the number below was arrived at. This is the column that keeps an estimate from being read
    -- as a measurement, and it is NOT NULL because "we did not record how we knew" is the state that
    -- makes every other row untrustworthy.
    resolution          varchar(32)   NOT NULL,

    -- Nullable only for UNKNOWN legs. A duration on an unknown leg would be the fabricated precision
    -- the brief forbids in as many words.
    duration_minutes    integer,

    -- Ordinal band rather than an amount: the KB curates bands for modes, not fares, and a number
    -- here would imply a price nobody quoted.
    cost_band           varchar(16),

    -- The KB template this leg was instantiated from. NULL for AREA_ESTIMATE and UNKNOWN, which is
    -- exactly the set of rows whose duration nobody curated.
    route_segment_id    uuid,

    -- UC-C3-03's discipline applied to mobility: the citation behind the claim, copied so the
    -- timeline can show it without re-reading the knowledge base.
    source_ref          varchar(200),

    instructions        text,

    -- UC-C3-11. The locale app pack for this leg's mode, already filtered through the suppression
    -- rules (UC-K14: no Uber where 滴滴 is the standard). An array rather than a join table because
    -- it is written once with the leg, read whole, and never queried by app.
    recommended_app_ids uuid[]        NOT NULL DEFAULT '{}',

    created_at          timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_itinerary_leg_day
        FOREIGN KEY (itinerary_day_id) REFERENCES itinerary_day (id) ON DELETE CASCADE,
    CONSTRAINT fk_itinerary_leg_from_item
        FOREIGN KEY (from_item_id) REFERENCES itinerary_item (id) ON DELETE CASCADE,
    CONSTRAINT fk_itinerary_leg_to_item
        FOREIGN KEY (to_item_id) REFERENCES itinerary_item (id) ON DELETE CASCADE,
    -- No cascade: recurating a route template must not delete somebody's plan for it. The leg keeps
    -- its mode, duration and instructions; only the citation goes stale, which a reader can see.
    CONSTRAINT fk_itinerary_leg_route_segment
        FOREIGN KEY (route_segment_id) REFERENCES route_segment (id) ON DELETE SET NULL,

    CONSTRAINT ck_itinerary_leg_mode CHECK (transport_mode IS NULL OR transport_mode IN (
        'WALK', 'METRO', 'TRAIN', 'BUS', 'TRAM', 'FERRY', 'TAXI', 'RIDESHARE', 'BIKE', 'CAR')),

    CONSTRAINT ck_itinerary_leg_resolution CHECK (resolution IN (
        'CURATED_SEGMENT', 'AREA_ESTIMATE', 'SAME_AREA_WALK', 'UNKNOWN')),

    CONSTRAINT ck_itinerary_leg_not_self CHECK (from_item_id <> to_item_id),

    CONSTRAINT ck_itinerary_leg_duration_positive CHECK (
        duration_minutes IS NULL OR duration_minutes >= 1),

    -- The two invariants that keep an honest fallback honest, at the level that cannot be skipped:
    -- an UNKNOWN leg claims no duration and no segment, and everything else claims a duration.
    CONSTRAINT ck_itinerary_leg_unknown_claims_nothing CHECK (
        resolution <> 'UNKNOWN'
            OR (duration_minutes IS NULL AND route_segment_id IS NULL
                AND cost_band IS NULL AND transport_mode IS NULL)),
    CONSTRAINT ck_itinerary_leg_resolved_has_duration CHECK (
        resolution = 'UNKNOWN' OR (duration_minutes IS NOT NULL AND transport_mode IS NOT NULL)),

    -- Only a CURATED_SEGMENT may cite one. An estimate pointing at a segment would be a measurement
    -- wearing a citation it did not earn.
    CONSTRAINT ck_itinerary_leg_segment_only_when_curated CHECK (
        route_segment_id IS NULL OR resolution = 'CURATED_SEGMENT'),

    CONSTRAINT ck_itinerary_leg_cost_band CHECK (
        cost_band IS NULL OR cost_band IN ('FREE', 'BUDGET', 'MODERATE', 'EXPENSIVE', 'LUXURY'))
);

-- One leg per ordered pair. The chain is item[n] → item[n+1], so a second row for the same pair is a
-- duplicate rather than an alternative; alternatives are a later feature and would need their own
-- discriminator.
CREATE UNIQUE INDEX uq_itinerary_leg_pair ON itinerary_leg (from_item_id, to_item_id);

-- The timeline read: every leg of a day, alongside V27's items index (UC-C3-08).
CREATE INDEX ix_itinerary_leg_day ON itinerary_leg (itinerary_day_id);
