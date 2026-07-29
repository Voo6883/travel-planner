-- V16 — how a traveller moves and what they install (TRAVEL-KNOWLEDGE-CATALOG §2.1, §3).
--
-- `route_segment` is curated for area pairs only (ADR 010 Consequences). Everything else is an
-- estimate, and the `estimated` flag below is what keeps that distinction visible: the null-leg
-- fallback must be explicit with mode heuristics rather than LLM-invented, so a leg the curator
-- never wrote has to announce itself as inferred.

CREATE TABLE transport_mode (
    id                uuid          PRIMARY KEY,
    destination_id    uuid          NOT NULL,
    slug              varchar(120)  NOT NULL,
    name              varchar(200)  NOT NULL,
    kind              varchar(32)   NOT NULL,
    description       text,
    -- Ordinal band for the same reason poi.price_band is: comparing raw fares across currencies
    -- and years is meaningless, and C3 only needs "is this the cheap option".
    cost_band         varchar(16),
    -- Whether a tourist can realistically use it without local language or residency — the sort of
    -- fact a guide states and an LLM guesses.
    tourist_friendly  boolean       NOT NULL DEFAULT true,
    source_id         uuid          NOT NULL,
    retrieved_at      timestamptz   NOT NULL,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at        timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_transport_mode_destination FOREIGN KEY (destination_id)
        REFERENCES destination (id) ON DELETE CASCADE,
    CONSTRAINT fk_transport_mode_source FOREIGN KEY (source_id)
        REFERENCES knowledge_source (id),
    CONSTRAINT uq_transport_mode_destination_slug UNIQUE (destination_id, slug),
    CONSTRAINT ck_transport_mode_kind CHECK (kind IN (
        'WALK', 'METRO', 'TRAIN', 'BUS', 'TRAM', 'FERRY', 'TAXI', 'RIDESHARE', 'BIKE', 'CAR'
    )),
    CONSTRAINT ck_transport_mode_cost_band CHECK (
        cost_band IS NULL OR cost_band IN ('FREE', 'BUDGET', 'MODERATE', 'EXPENSIVE', 'LUXURY')
    )
);

CREATE INDEX ix_transport_mode_destination ON transport_mode (destination_id);

-- ---------------------------------------------------------------------------------------------
-- route_segment — a curated leg between two areas.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE route_segment (
    id                uuid          PRIMARY KEY,
    destination_id    uuid          NOT NULL,
    from_area_id      uuid          NOT NULL,
    to_area_id        uuid          NOT NULL,
    transport_mode_id uuid          NOT NULL,
    duration_minutes  integer       NOT NULL,
    -- ADR 010 Consequences: a leg the curator did not author is served as an estimate derived from
    -- mode heuristics, never as a curated fact and never invented by the model. Defaulting to true
    -- means a row inserted without thinking about it claims LESS, not more.
    estimated         boolean       NOT NULL DEFAULT true,
    notes             text,
    source_id         uuid          NOT NULL,
    retrieved_at      timestamptz   NOT NULL,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at        timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_route_segment_destination FOREIGN KEY (destination_id)
        REFERENCES destination (id) ON DELETE CASCADE,
    CONSTRAINT fk_route_segment_from_area FOREIGN KEY (from_area_id)
        REFERENCES destination_area (id) ON DELETE CASCADE,
    CONSTRAINT fk_route_segment_to_area FOREIGN KEY (to_area_id)
        REFERENCES destination_area (id) ON DELETE CASCADE,
    CONSTRAINT fk_route_segment_mode FOREIGN KEY (transport_mode_id)
        REFERENCES transport_mode (id),
    CONSTRAINT fk_route_segment_source FOREIGN KEY (source_id)
        REFERENCES knowledge_source (id),
    -- One curated leg per (pair, mode). The same two areas may be connected by metro and by ferry;
    -- they may not be connected twice by metro.
    CONSTRAINT uq_route_segment_pair_mode UNIQUE (from_area_id, to_area_id, transport_mode_id),
    -- A segment from an area to itself is a data-entry slip, and would surface as a zero-length leg
    -- in an itinerary rather than as an error.
    CONSTRAINT ck_route_segment_distinct_areas CHECK (from_area_id <> to_area_id),
    CONSTRAINT ck_route_segment_duration_positive CHECK (duration_minutes > 0)
);

CREATE INDEX ix_route_segment_destination ON route_segment (destination_id);
CREATE INDEX ix_route_segment_from_area ON route_segment (from_area_id);

-- ---------------------------------------------------------------------------------------------
-- travel_app — the per-country app packs (TRAVEL-KNOWLEDGE-CATALOG §2.1).
--
-- Scoped by country rather than by destination: Grab is useful across Thailand, not only in
-- Bangkok, and duplicating it per city would make a correction an N-row edit.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE travel_app (
    id            uuid          PRIMARY KEY,
    country_code  char(2)       NOT NULL,
    slug          varchar(120)  NOT NULL,
    name          varchar(200)  NOT NULL,
    category      varchar(32)   NOT NULL,
    description   text,
    ios_url       varchar(1000),
    android_url   varchar(1000),
    source_id     uuid          NOT NULL,
    -- ADR 010 §6: 180-day TTL. Store URLs rot quietly — an app delisted in one market still
    -- returns a page, so age is the only signal available.
    retrieved_at  timestamptz   NOT NULL,
    created_at    timestamptz   NOT NULL DEFAULT now(),
    updated_at    timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_travel_app_source FOREIGN KEY (source_id) REFERENCES knowledge_source (id),
    CONSTRAINT uq_travel_app_country_slug UNIQUE (country_code, slug),
    CONSTRAINT ck_travel_app_category CHECK (category IN (
        'RIDEHAILING', 'TRANSIT', 'PAYMENT', 'FOOD_DELIVERY', 'TRANSLATION', 'NAVIGATION', 'ESIM'
    )),
    -- An app entry with neither store link is not actionable; the whole point of the pack is
    -- "install this before you fly".
    CONSTRAINT ck_travel_app_has_a_store_link CHECK (
        ios_url IS NOT NULL OR android_url IS NOT NULL
    )
);

CREATE INDEX ix_travel_app_country ON travel_app (country_code);
