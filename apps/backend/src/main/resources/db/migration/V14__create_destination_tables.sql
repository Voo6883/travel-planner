-- V14 — destination, guide, and area (ADR 010 §4, PLAN §4.1.2).
--
-- `coverage_level` is the reason this table is not just a lookup list. ADR 010 §4 turns "we have no
-- data for Osaka" from a silent low fitScore into a typed outcome: only FULL destinations enter C2
-- ranking, and anything else answers `destination_not_covered` with the supported list. A
-- destination that is merely absent from the ranking is indistinguishable from one that ranked
-- badly, which is the failure the coverage model exists to prevent.

CREATE TABLE destination (
    id             uuid          PRIMARY KEY,
    -- Stable, human-readable handle ('tokyo-jp'). Seed files and the supported-destinations
    -- endpoint address destinations by this, so re-seeding never depends on generated ids.
    slug           varchar(120)  NOT NULL,
    name           varchar(200)  NOT NULL,
    -- ISO 3166-1 alpha-2. Fixed width so a three-letter code cannot be stored by accident.
    country_code   char(2)       NOT NULL,
    -- IANA zone, e.g. 'Asia/Tokyo'. Needed before any itinerary can place an event on a clock
    -- (task 28); stored here because it is a property of the place, not of a trip.
    timezone       varchar(64)   NOT NULL,
    latitude       numeric(9, 6),
    longitude      numeric(9, 6),
    coverage_level varchar(16)   NOT NULL DEFAULT 'NONE',
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_destination_slug UNIQUE (slug),

    -- Owned by com.travelplanner.domain.enums.CoverageLevel; a test asserts the enum against this
    -- list so the two cannot drift.
    CONSTRAINT ck_destination_coverage_level CHECK (coverage_level IN ('FULL', 'PARTIAL', 'NONE')),

    -- Both or neither. A half-set coordinate silently places a destination on the equator or the
    -- prime meridian, which looks like data rather than like the mistake it is.
    CONSTRAINT ck_destination_coordinates_paired CHECK (
        (latitude IS NULL) = (longitude IS NULL)
    ),
    CONSTRAINT ck_destination_latitude_range CHECK (
        latitude IS NULL OR (latitude >= -90 AND latitude <= 90)
    ),
    CONSTRAINT ck_destination_longitude_range CHECK (
        longitude IS NULL OR (longitude >= -180 AND longitude <= 180)
    )
);

-- The C2 ranking query reads only FULL destinations (ADR 010 §4). Partial, because the whole point
-- is that the other two states are never scanned for ranking.
CREATE INDEX ix_destination_coverage_full ON destination (slug) WHERE coverage_level = 'FULL';

-- ---------------------------------------------------------------------------------------------
-- destination_guide — the narrative body, split into the three field groups ADR 010 §5 embeds
-- separately. They are columns rather than one JSON blob precisely because they are embedded
-- apart: `overview`, `food` and `practical` answer different questions, and a single embedding
-- over all three produces the unusable centroid ADR 010 rejects under "Alternatives considered".
-- ---------------------------------------------------------------------------------------------
CREATE TABLE destination_guide (
    id             uuid          PRIMARY KEY,
    destination_id uuid          NOT NULL,
    -- ADR 010 §5: `en` is authoritative for v1 embeddings; `ms` UI strings are translated at
    -- presentation and never embedded. The column exists now so task 40 can add a locale without
    -- a schema change.
    locale         varchar(16)   NOT NULL DEFAULT 'en',
    overview       text          NOT NULL,
    food           text,
    practical      text,
    source_id      uuid          NOT NULL,
    -- ADR 010 §6: narrative TTL is 730 days, measured from the source fetch. Denormalised onto the
    -- row so a staleness read does not join every guide to its source.
    retrieved_at   timestamptz   NOT NULL,
    version        integer       NOT NULL DEFAULT 0,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_destination_guide_destination FOREIGN KEY (destination_id)
        REFERENCES destination (id) ON DELETE CASCADE,
    -- No ON DELETE CASCADE, and no SET NULL: a source may not be deleted while rows still cite it.
    -- Losing provenance is worse than an awkward delete (PLAN §4.1.0).
    CONSTRAINT fk_destination_guide_source FOREIGN KEY (source_id)
        REFERENCES knowledge_source (id),
    -- One guide per destination per locale. The uniqueness is what lets task 41's curation UI
    -- update rather than accumulate duplicates.
    CONSTRAINT uq_destination_guide_destination_locale UNIQUE (destination_id, locale),
    CONSTRAINT ck_destination_guide_version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_destination_guide_overview_not_blank CHECK (length(btrim(overview)) > 0)
);

-- ---------------------------------------------------------------------------------------------
-- destination_area — the neighbourhood level C3 schedules against (TRAVEL-KNOWLEDGE-CATALOG §2).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE destination_area (
    id             uuid          PRIMARY KEY,
    destination_id uuid          NOT NULL,
    slug           varchar(120)  NOT NULL,
    name           varchar(200)  NOT NULL,
    description    text,
    latitude       numeric(9, 6),
    longitude      numeric(9, 6),
    source_id      uuid          NOT NULL,
    retrieved_at   timestamptz   NOT NULL,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_destination_area_destination FOREIGN KEY (destination_id)
        REFERENCES destination (id) ON DELETE CASCADE,
    CONSTRAINT fk_destination_area_source FOREIGN KEY (source_id)
        REFERENCES knowledge_source (id),
    -- Slugs are unique within a destination, not globally: 'downtown' is a reasonable area name in
    -- more than one city.
    CONSTRAINT uq_destination_area_destination_slug UNIQUE (destination_id, slug),
    CONSTRAINT ck_destination_area_coordinates_paired CHECK (
        (latitude IS NULL) = (longitude IS NULL)
    )
);

CREATE INDEX ix_destination_area_destination ON destination_area (destination_id);
