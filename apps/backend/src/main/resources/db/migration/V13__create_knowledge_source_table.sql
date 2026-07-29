-- V13 — provenance (ADR 010 §2, PLAN §4.1.0 "provenance on every field").
--
-- First of the knowledge migrations, and first on purpose: every factual row in the TKB references
-- this table, so it has to exist before any of them. The ordering is the schema stating the
-- product rule — a fact with no source cannot be inserted, because the foreign key has nowhere to
-- point.
--
-- `licence` is not decoration. ADR 010 §2 allows Wikivoyage (CC BY-SA 4.0) and OSM/Nominatim
-- (ODbL), both of which carry attribution obligations, and CC BY-SA additionally propagates
-- share-alike into DERIVED guide text. That makes attribution a product constraint surfaced in the
-- UI rather than a legal footnote, which is why `attribution_text` is NOT NULL: there is no valid
-- source without the string a page must display.

CREATE TABLE knowledge_source (
    id               uuid          PRIMARY KEY,
    -- Stable handle used by seed files and by the reserved stub marker. Task 17 addresses sources
    -- by this rather than by uuid, so a re-seed does not have to preserve generated ids.
    source_ref       varchar(160)  NOT NULL,
    name             varchar(200)  NOT NULL,
    licence          varchar(64)   NOT NULL,
    attribution_text varchar(500)  NOT NULL,
    -- Null only for the reserved stub source, which deliberately has no plausible URL: ADR 010 §3
    -- forbids fabricated provenance, so sample rows cite `stub:sample` and nothing resembling a
    -- real page.
    source_url       varchar(1000),
    -- When the fact was taken from the source. Distinct from created_at: a row imported today may
    -- describe a page fetched last month, and ADR 010 §6's TTLs are measured against the fetch.
    retrieved_at     timestamptz   NOT NULL,
    trust_tier       varchar(32)   NOT NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_knowledge_source_ref UNIQUE (source_ref),

    -- Enumerated in a CHECK rather than a Postgres ENUM, matching V5's reasoning: adding a value
    -- to a native enum needs DDL that cannot run in every migration context. The vocabulary is
    -- owned by com.travelplanner.domain.enums.KnowledgeLicence and asserted against this list by a
    -- test, so the two cannot drift silently.
    --
    -- PROPRIETARY_FORBIDDEN exists so that a source which must never be persisted (Google Places,
    -- per ADR 010 §2) is nameable in a rejection message rather than absent from the vocabulary.
    CONSTRAINT ck_knowledge_source_licence CHECK (licence IN (
        'CC_BY_SA_4_0',
        'ODBL',
        'OPERATOR_TERMS',
        'SAMPLE_DATA',
        'PROPRIETARY_FORBIDDEN'
    )),

    -- OFFICIAL  — tourism board or operator publishing its own hours and fares
    -- COMMUNITY  — Wikivoyage, OSM: high coverage, variable currency
    -- SAMPLE     — the reserved stub tier; ADR 010 §3 requires these to be flagged, never to look real
    CONSTRAINT ck_knowledge_source_trust_tier CHECK (trust_tier IN (
        'OFFICIAL',
        'COMMUNITY',
        'SAMPLE'
    )),

    -- A forbidden licence must never reach a stored row. The check is here rather than in a service
    -- because ADR 010 §2's rule is "forbidden to persist" — a database-level refusal is the only
    -- version of that rule which survives somebody writing a new adapter.
    CONSTRAINT ck_knowledge_source_not_forbidden CHECK (licence <> 'PROPRIETARY_FORBIDDEN'),

    -- Sample data and the SAMPLE tier imply each other. Without this, a stub row could be inserted
    -- carrying COMMUNITY trust and would be indistinguishable from curated content downstream.
    CONSTRAINT ck_knowledge_source_sample_alignment CHECK (
        (licence = 'SAMPLE_DATA') = (trust_tier = 'SAMPLE')
    )
);

-- Task 40 re-reads sources by age to decide what to refresh (ADR 010 §6).
CREATE INDEX ix_knowledge_source_retrieved_at ON knowledge_source (retrieved_at);
