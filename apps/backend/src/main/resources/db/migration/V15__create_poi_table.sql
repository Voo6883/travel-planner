-- V15 — points of interest (TRAVEL-KNOWLEDGE-CATALOG §2, ADR 010 §6).
--
-- The freshness columns carry the most operational weight in the whole TKB. ADR 010 §6 gives
-- `opening_hours` and `price_band` a 90-day TTL and requires the agent to stop asserting them as
-- current once expired — "a stale opening-hours presented as fact is the most direct way to ruin a
-- traveller's day". `retrieved_at` is therefore NOT NULL and denormalised onto the row: staleness
-- has to be answerable from the row being returned, not from a join the caller might omit.

CREATE TABLE poi (
    id             uuid          PRIMARY KEY,
    destination_id uuid          NOT NULL,
    -- Nullable: not every POI sits inside a curated area, and forcing one would invent geography.
    area_id        uuid,
    slug           varchar(160)  NOT NULL,
    name           varchar(300)  NOT NULL,
    -- ADR 010 §5: POI embeds `name + description + tags` as one chunk, so all three live here.
    description    text,
    category       varchar(64)   NOT NULL,
    -- text[] rather than a join table: tags are a closed, small, read-mostly set that is embedded
    -- alongside the name, never queried on their own. A join table would add a hop to every read
    -- for no query that exists.
    tags           text[]        NOT NULL DEFAULT '{}',
    locale         varchar(16)   NOT NULL DEFAULT 'en',
    latitude       numeric(9, 6),
    longitude      numeric(9, 6),
    -- Free text on purpose. Real opening hours are irregular ("closed 2nd Tuesday, 11:00-14:30 in
    -- winter") and a structured model that cannot express the exception invites a confident wrong
    -- answer. The agent quotes this string or says it does not know.
    opening_hours  varchar(500),
    -- Ordinal band, not a currency amount: comparing a 2019 yen figure against a 2026 baht one is
    -- meaningless, and price_history (V17) is where actual money belongs.
    price_band     varchar(16),
    source_id      uuid          NOT NULL,
    retrieved_at   timestamptz   NOT NULL,
    version        integer       NOT NULL DEFAULT 0,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_poi_destination FOREIGN KEY (destination_id)
        REFERENCES destination (id) ON DELETE CASCADE,
    -- SET NULL rather than CASCADE: removing an area must not delete the restaurants inside it.
    CONSTRAINT fk_poi_area FOREIGN KEY (area_id)
        REFERENCES destination_area (id) ON DELETE SET NULL,
    CONSTRAINT fk_poi_source FOREIGN KEY (source_id) REFERENCES knowledge_source (id),
    CONSTRAINT uq_poi_destination_slug UNIQUE (destination_id, slug),

    -- Owned by com.travelplanner.domain.enums.PoiCategory; asserted against this list by a test.
    -- FOOD is separated from the rest because ADR 010 §1 sets a floor of >=8 food POIs per
    -- destination, so it is a category the seed validator counts.
    CONSTRAINT ck_poi_category CHECK (category IN (
        'FOOD',
        'SIGHT',
        'MUSEUM',
        'NATURE',
        'SHOPPING',
        'NIGHTLIFE',
        'EXPERIENCE',
        'TRANSPORT_HUB'
    )),
    CONSTRAINT ck_poi_price_band CHECK (
        price_band IS NULL OR price_band IN ('FREE', 'BUDGET', 'MODERATE', 'EXPENSIVE', 'LUXURY')
    ),
    CONSTRAINT ck_poi_coordinates_paired CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT ck_poi_latitude_range CHECK (
        latitude IS NULL OR (latitude >= -90 AND latitude <= 90)
    ),
    CONSTRAINT ck_poi_longitude_range CHECK (
        longitude IS NULL OR (longitude >= -180 AND longitude <= 180)
    ),
    CONSTRAINT ck_poi_version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_poi_name_not_blank CHECK (length(btrim(name)) > 0)
);

-- Retrieval is always scoped to one destination first (ADR 010 §5's pre-filter rule), and the
-- category filter is what a "where should I eat" query narrows to next.
CREATE INDEX ix_poi_destination_category ON poi (destination_id, category);
CREATE INDEX ix_poi_area ON poi (area_id) WHERE area_id IS NOT NULL;

-- ADR 010 §5 requires hybrid search: vector similarity fused with Postgres full-text, because the
-- plan's own worked examples ("street food", "temples") are lexical and underperform under pure
-- vector search. Expression index rather than a stored tsvector column so there is no second copy
-- of the text to keep in step.
CREATE INDEX ix_poi_fulltext ON poi
    USING gin (to_tsvector('simple', coalesce(name, '') || ' ' || coalesce(description, '')));

-- Task 40 refreshes by age (ADR 010 §6: 90-day TTL on hours and price band).
CREATE INDEX ix_poi_retrieved_at ON poi (retrieved_at);
