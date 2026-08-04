-- V25 — ranked recommendations from a C2 research run (PLAN §4.1 / §8, UC-C2-03/04/05/10,
-- tasks/25). Task 26 owns the list/select HTTP surface; this migration is the persistence contract
-- that surface will read.
--
-- A research job that reaches RESEARCH_READY must leave a durable outcome: either one or more
-- ranked_recommendation rows attributed to research_run_id, or a research_run_result row with
-- no_confident_result = true (UC-C2-05 typed empty). The completion hook and the handler share
-- that invariant so a client never sees RESEARCH_READY with nothing to fetch.

-- research_run_id equals research_job.id at creation (V24) but is its own column so recommendations
-- can attribute a run without assuming id == run. A UNIQUE index is required before anything can
-- FK to it.
CREATE UNIQUE INDEX uq_research_job_research_run_id ON research_job (research_run_id);

-- --------------------------------------------------------------------------------------------
-- research_run_result — one row per completed research run (attribution key = research_run_id).
-- --------------------------------------------------------------------------------------------
CREATE TABLE research_run_result (
    research_run_id     uuid         PRIMARY KEY,

    trip_id             uuid         NOT NULL,
    user_id             uuid         NOT NULL,

    -- UC-C2-05: typed empty when the DSA produced no confident recommendation. When true there are
    -- zero ranked_recommendation rows for this run; when false there is at least one.
    no_confident_result boolean      NOT NULL,

    -- DestinationRanker.ALGORITHM_VERSION at the time of the run.
    algorithm_version   varchar(64)  NOT NULL,

    -- Prompt / model metadata for the narrative half (LLM writes rationale / traveler_guide only).
    prompt_template_id  varchar(120) NOT NULL,
    prompt_version      integer      NOT NULL,
    model_name          varchar(120) NOT NULL,

    -- Exclusions from the ranker, as JSON (slug + reason + optional score snapshot). Empty array
    -- when none were excluded. Never the sole source of no_confident_result — that flag is explicit.
    excluded_json       jsonb        NOT NULL DEFAULT '[]'::jsonb,

    created_at          timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_research_run_result_trip
        FOREIGN KEY (trip_id) REFERENCES trip (id) ON DELETE CASCADE,
    CONSTRAINT fk_research_run_result_user
        FOREIGN KEY (user_id) REFERENCES "user" (id) ON DELETE CASCADE,
    CONSTRAINT fk_research_run_result_job
        FOREIGN KEY (research_run_id) REFERENCES research_job (research_run_id) ON DELETE CASCADE,
    CONSTRAINT ck_research_run_result_prompt_version_non_negative
        CHECK (prompt_version >= 0),
    CONSTRAINT ck_research_run_result_algorithm_version_not_blank
        CHECK (char_length(btrim(algorithm_version)) > 0)
);

CREATE INDEX ix_research_run_result_trip_created
    ON research_run_result (trip_id, created_at DESC);

-- --------------------------------------------------------------------------------------------
-- ranked_recommendation — one scored destination proposal per research run.
-- --------------------------------------------------------------------------------------------
CREATE TABLE ranked_recommendation (
    id                  uuid           PRIMARY KEY,

    trip_id             uuid           NOT NULL,
    user_id             uuid           NOT NULL,
    research_run_id     uuid           NOT NULL,

    destination_id      uuid           NOT NULL,
    destination_slug    varchar(120)   NOT NULL,
    country_code        char(2)        NOT NULL,

    -- 1-based rank within the run (stable DSA order).
    rank                integer        NOT NULL,

    -- ScoreBreakdown terms — persisted so task 26 can explain fit without re-running the DSA.
    -- numeric rather than float (PLAN §4.0.2-A / MigrationContractTest).
    fit_score           numeric(12, 8) NOT NULL,
    interest_match      numeric(8, 6)  NOT NULL,
    seasonality_fit     numeric(8, 6)  NOT NULL,
    price_fit           numeric(8, 6)  NOT NULL,
    area_coverage       numeric(8, 6)  NOT NULL,
    freshness_factor   numeric(8, 6)  NOT NULL,
    confidence          numeric(8, 6)  NOT NULL,

    est_cost_amount     numeric(19, 4),
    est_cost_currency   char(3),

    rationale           text           NOT NULL,
    traveler_guide      jsonb          NOT NULL,
    risks               jsonb          NOT NULL DEFAULT '[]'::jsonb,
    best_window         varchar(120),
    source_refs         jsonb          NOT NULL,

    algorithm_version   varchar(64)    NOT NULL,

    created_at          timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT fk_ranked_recommendation_trip
        FOREIGN KEY (trip_id) REFERENCES trip (id) ON DELETE CASCADE,
    CONSTRAINT fk_ranked_recommendation_user
        FOREIGN KEY (user_id) REFERENCES "user" (id) ON DELETE CASCADE,
    CONSTRAINT fk_ranked_recommendation_run
        FOREIGN KEY (research_run_id) REFERENCES research_run_result (research_run_id)
            ON DELETE CASCADE,
    CONSTRAINT fk_ranked_recommendation_destination
        FOREIGN KEY (destination_id) REFERENCES destination (id),

    CONSTRAINT ck_ranked_recommendation_rank_positive CHECK (rank >= 1),
    CONSTRAINT ck_ranked_recommendation_fit_score_non_negative CHECK (fit_score >= 0),
    CONSTRAINT ck_ranked_recommendation_scores_unit CHECK (
        interest_match BETWEEN 0 AND 1
        AND seasonality_fit BETWEEN 0 AND 1
        AND price_fit BETWEEN 0 AND 1
        AND area_coverage BETWEEN 0 AND 1
        AND freshness_factor BETWEEN 0 AND 1
        AND confidence BETWEEN 0 AND 1
    ),
    CONSTRAINT ck_ranked_recommendation_est_cost_paired CHECK (
        (est_cost_amount IS NULL) = (est_cost_currency IS NULL)
    ),
    CONSTRAINT ck_ranked_recommendation_rationale_not_blank
        CHECK (char_length(btrim(rationale)) > 0),
    CONSTRAINT ck_ranked_recommendation_algorithm_version_not_blank
        CHECK (char_length(btrim(algorithm_version)) > 0),

    -- One destination per run; rank is unique within the run.
    CONSTRAINT uq_ranked_recommendation_run_destination
        UNIQUE (research_run_id, destination_id),
    CONSTRAINT uq_ranked_recommendation_run_rank
        UNIQUE (research_run_id, rank)
);

CREATE INDEX ix_ranked_recommendation_trip_run
    ON ranked_recommendation (trip_id, research_run_id, rank);

-- Trip.selected_recommendation_id was deliberately left without an FK in V5 (ranked_recommendation
-- did not exist yet). Now that the table exists, wire the constraint so a selection cannot point at
-- a row that was never written. ON DELETE SET NULL: deleting history for a run must not delete the
-- trip; selection simply clears.
ALTER TABLE trip
    ADD CONSTRAINT fk_trip_selected_recommendation
        FOREIGN KEY (selected_recommendation_id)
        REFERENCES ranked_recommendation (id)
        ON DELETE SET NULL;
