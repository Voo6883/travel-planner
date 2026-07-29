-- V17 — when to go and what it costs (PLAN §4.1.2, ADR 010 §6).
--
-- Both tables feed C2's fitScore. ADR 010 §6 gives them a 365-day TTL and a refresh job rather
-- than a staleness flag, because a year-old seasonality row is still broadly true while a
-- year-old opening time is not.

CREATE TABLE seasonality (
    id                uuid          PRIMARY KEY,
    destination_id    uuid          NOT NULL,
    -- 1-12. A month number rather than a date: seasonality is a recurring shape, not an event, and
    -- storing 2026-03-01 would invite somebody to filter it by year.
    month             smallint      NOT NULL,
    -- Ordinal bands rather than raw figures. The curated sources state "hot and wet" or "peak
    -- season", and inventing a temperature to store would be exactly the fabrication PLAN §4.1.0
    -- forbids.
    weather_band      varchar(16)   NOT NULL,
    crowd_band        varchar(16)   NOT NULL,
    price_band        varchar(16)   NOT NULL,
    notes             text,
    source_id         uuid          NOT NULL,
    retrieved_at      timestamptz   NOT NULL,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at        timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_seasonality_destination FOREIGN KEY (destination_id)
        REFERENCES destination (id) ON DELETE CASCADE,
    CONSTRAINT fk_seasonality_source FOREIGN KEY (source_id) REFERENCES knowledge_source (id),
    -- One row per destination-month. ADR 010 §1 requires all 12 for a FULL destination, and this
    -- constraint is what makes "12 rows" mean "the whole year" rather than "twelve rows about June".
    CONSTRAINT uq_seasonality_destination_month UNIQUE (destination_id, month),
    CONSTRAINT ck_seasonality_month_range CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_seasonality_weather_band CHECK (weather_band IN (
        'COLD', 'COOL', 'MILD', 'WARM', 'HOT', 'WET', 'STORMY'
    )),
    CONSTRAINT ck_seasonality_crowd_band CHECK (crowd_band IN ('LOW', 'MODERATE', 'HIGH', 'PEAK')),
    CONSTRAINT ck_seasonality_price_band CHECK (
        price_band IN ('FREE', 'BUDGET', 'MODERATE', 'EXPENSIVE', 'LUXURY')
    )
);

CREATE INDEX ix_seasonality_destination ON seasonality (destination_id);

-- ---------------------------------------------------------------------------------------------
-- price_history — the only place in the TKB holding actual money.
--
-- Money is numeric(12, 2) with an explicit ISO 4217 currency, never a float and never a bare
-- number: a price without its currency is not a price, and binary floating point cannot represent
-- 0.10 exactly, which is how rounding errors reach a quoted total.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE price_history (
    id                uuid           PRIMARY KEY,
    destination_id    uuid           NOT NULL,
    -- What the figure is about: 'HOTEL_NIGHT', 'MEAL_MID_RANGE', 'TRANSIT_DAY_PASS'. A closed
    -- vocabulary would have to be extended by migration every time curation learns a new category,
    -- so this is free text with a uniqueness constraint doing the discipline instead.
    category          varchar(64)    NOT NULL,
    amount            numeric(12, 2) NOT NULL,
    currency          char(3)        NOT NULL,
    -- The month the figure describes, stored as its first day. A range would be more precise and
    -- less usable; every source quotes monthly averages.
    observed_on       date           NOT NULL,
    source_id         uuid           NOT NULL,
    retrieved_at      timestamptz    NOT NULL,
    created_at        timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT fk_price_history_destination FOREIGN KEY (destination_id)
        REFERENCES destination (id) ON DELETE CASCADE,
    CONSTRAINT fk_price_history_source FOREIGN KEY (source_id) REFERENCES knowledge_source (id),
    -- One observation per destination, category and month. A second one is a correction, not a
    -- second truth, so it replaces rather than accumulates.
    CONSTRAINT uq_price_history_observation UNIQUE (destination_id, category, observed_on),
    CONSTRAINT ck_price_history_amount_positive CHECK (amount > 0),
    -- Uppercase ISO 4217. Without this, 'jpy' and 'JPY' become two currencies.
    CONSTRAINT ck_price_history_currency_upper CHECK (currency = upper(currency)),
    CONSTRAINT ck_price_history_observed_on_first_of_month CHECK (
        extract(day from observed_on) = 1
    )
);

CREATE INDEX ix_price_history_destination_category
    ON price_history (destination_id, category, observed_on DESC);
